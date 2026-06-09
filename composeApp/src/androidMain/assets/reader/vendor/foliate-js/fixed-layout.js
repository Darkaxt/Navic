const parseViewport = str => str
    ?.split(/[,;\s]/) // NOTE: technically, only the comma is valid
    ?.filter(x => x)
    ?.map(x => x.split('=').map(x => x.trim()))

const toViewport = viewport => {
    if (typeof viewport === 'string') {
        const parsed = parseViewport(viewport)
        return parsed ? Object.fromEntries(parsed) : null
    }
    return viewport
}

const getViewport = async (doc, viewport) => {
    // use `viewBox` for SVG
    if (doc.documentElement.localName === 'svg') {
        const [, , width, height] = doc.documentElement
            .getAttribute('viewBox')?.split(/\s/) ?? []
        return { width, height }
    }

    // get `viewport` `meta` element
    const meta = parseViewport(doc.querySelector('meta[name="viewport"]')
        ?.getAttribute('content'))
    if (meta) return Object.fromEntries(meta)

    // fallback to book's viewport
    const defaultViewport = toViewport(viewport)
    if (defaultViewport) return defaultViewport

    // if no viewport (possibly with image directly in spine), get image size
    const img = doc.querySelector('img')
    if (img) {
        if ((!img.naturalWidth || !img.naturalHeight) && typeof img.decode === 'function') {
            try {
                await img.decode()
            } catch (error) {
                console.warn('[FoliateFXL] image decode before viewport measurement failed', error)
            }
        }
        return { width: img.naturalWidth, height: img.naturalHeight }
    }

    // just show *something*, i guess...
    console.warn(new Error('Missing viewport properties'))
    return { width: 1000, height: 2000 }
}

const normalizeFrameSize = (size, fallback = 1000) => {
    const value = parseFloat(size)
    if (Number.isFinite(value) && value > 0) return value
    const fallbackValue = parseFloat(fallback)
    return Number.isFinite(fallbackValue) && fallbackValue > 0 ? fallbackValue : 1000
}

const hostStyles = `:host {
    width: 100%;
    height: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: auto;
}`

const applyShadowStyles = root => {
    if ('adoptedStyleSheets' in root && typeof CSSStyleSheet !== 'undefined') {
        const sheet = new CSSStyleSheet()
        sheet.replaceSync(hostStyles)
        root.adoptedStyleSheets = [sheet]
    } else {
        const style = document.createElement('style')
        style.textContent = hostStyles
        root.append(style)
    }
}

export class FixedLayout extends HTMLElement {
    static observedAttributes = ['zoom']
    #root = this.attachShadow({ mode: 'closed' })
    #observer = new ResizeObserver(() => this.#render())
    #spreads
    #index = -1
    defaultViewport
    spread
    #portrait = false
    #left
    #right
    #center
    #side
    #zoom
    constructor() {
        super()

        applyShadowStyles(this.#root)

        this.#observer.observe(this)
    }
    attributeChangedCallback(name, _, value) {
        switch (name) {
            case 'zoom':
                this.#zoom = value !== 'fit-width' && value !== 'fit-page'
                    ? parseFloat(value) : value
                this.#render()
                break
        }
    }
    async #createFrame({ index, src: srcOption }) {
        const srcOptionIsString = typeof srcOption === 'string'
        const src = srcOptionIsString ? srcOption : srcOption?.src
        const onZoom = srcOptionIsString ? null : srcOption?.onZoom
        const element = document.createElement('div')
        const iframe = document.createElement('iframe')
        element.append(iframe)
        Object.assign(iframe.style, {
            border: '0',
            display: 'none',
            overflow: 'hidden',
        })
        // `allow-scripts` is needed for events because of WebKit bug
        // https://bugs.webkit.org/show_bug.cgi?id=218086
        iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts')
        iframe.setAttribute('scrolling', 'no')
        iframe.setAttribute('part', 'filter')
        this.#root.append(element)
        if (!src) return { blank: true, element, iframe }
        return new Promise(resolve => {
            iframe.addEventListener('load', async () => {
                const doc = iframe.contentDocument
                this.dispatchEvent(new CustomEvent('load', { detail: { doc, index } }))
                const { width, height } = await getViewport(doc, this.defaultViewport)
                const frameWidth = normalizeFrameSize(width)
                const frameHeight = normalizeFrameSize(height, frameWidth * 1.5)
                const img = doc?.querySelector('img')
                const imageState = img
                    ? ` imageComplete=${img.complete} imageNatural=${img.naturalWidth}x${img.naturalHeight}`
                    : ' image=none'
                console.info(`[FoliateFXL] frame-loaded index=${index} width=${frameWidth} height=${frameHeight}${imageState}`)
                resolve({
                    element, iframe,
                    width: frameWidth,
                    height: frameHeight,
                    onZoom,
                })
            }, { once: true })
            iframe.src = src
        })
    }
    #render(side = this.#side) {
        if (!side) return
        const left = this.#left ?? {}
        const right = this.#center ?? this.#right
        const target = side === 'left' ? left : right
        const { width, height } = this.getBoundingClientRect()
        const portrait = this.spread !== 'both' && this.spread !== 'portrait'
            && height > width
        this.#portrait = portrait
        const viewportWidth = normalizeFrameSize(width)
        const viewportHeight = normalizeFrameSize(height, 2000)
        const blankWidth = normalizeFrameSize(left.width ?? right.width)
        const blankHeight = normalizeFrameSize(left.height ?? right.height, blankWidth * 1.5)
        const targetWidth = normalizeFrameSize(target.width ?? blankWidth, blankWidth)
        const targetHeight = normalizeFrameSize(target.height ?? blankHeight, blankHeight)
        const leftWidth = normalizeFrameSize(left.width ?? blankWidth, blankWidth)
        const leftHeight = normalizeFrameSize(left.height ?? blankHeight, blankHeight)
        const rightWidth = normalizeFrameSize(right.width ?? blankWidth, blankWidth)
        const rightHeight = normalizeFrameSize(right.height ?? blankHeight, blankHeight)

        const scale = typeof this.#zoom === 'number' && !isNaN(this.#zoom)
            ? this.#zoom
            : this.#zoom === 'fit-width' ? (portrait || this.#center
                ? viewportWidth / targetWidth
                : viewportWidth / (leftWidth + rightWidth))
            : (portrait || this.#center
                ? Math.min(
                    viewportWidth / targetWidth,
                    viewportHeight / targetHeight)
                : Math.min(
                    viewportWidth / (leftWidth + rightWidth),
                    viewportHeight / Math.max(leftHeight, rightHeight)))

        const safeScale = Number.isFinite(scale) && scale > 0 ? scale : 1
        if (safeScale !== scale) {
            console.warn(`[FoliateFXL] invalid-scale width=${viewportWidth} height=${viewportHeight} targetWidth=${targetWidth} targetHeight=${targetHeight} scale=${scale}`)
        }
        console.debug(`[FoliateFXL] render side=${side} viewport=${viewportWidth}x${viewportHeight} target=${targetWidth}x${targetHeight} scale=${safeScale}`)

        const transform = frame => {
            let { element, iframe, width, height, blank, onZoom } = frame
            const frameWidth = normalizeFrameSize(width, blankWidth)
            const frameHeight = normalizeFrameSize(height, blankHeight)
            if (onZoom) onZoom({ doc: frame.iframe.contentDocument, scale: safeScale })
            const iframeScale = onZoom ? safeScale : 1
            Object.assign(iframe.style, {
                width: `${frameWidth * iframeScale}px`,
                height: `${frameHeight * iframeScale}px`,
                transform: onZoom ? 'none' : `scale(${safeScale})`,
                transformOrigin: 'top left',
                display: blank ? 'none' : 'block',
            })
            Object.assign(element.style, {
                width: `${frameWidth * safeScale}px`,
                height: `${frameHeight * safeScale}px`,
                overflow: 'hidden',
                display: 'block',
                flexShrink: '0',
                marginBlock: 'auto',
            })
            if (portrait && frame !== target) {
                element.style.display = 'none'
            }
        }
        if (this.#center) {
            transform(this.#center)
        } else {
            transform(left)
            transform(right)
        }
    }
    async #showSpread({ left, right, center, side }) {
        this.#root.replaceChildren()
        this.#left = null
        this.#right = null
        this.#center = null
        if (center) {
            this.#center = await this.#createFrame(center)
            this.#side = 'center'
            this.#render()
        } else {
            this.#left = await this.#createFrame(left)
            this.#right = await this.#createFrame(right)
            this.#side = this.#left.blank ? 'right'
                : this.#right.blank ? 'left' : side
            this.#render()
        }
    }
    #goLeft() {
        if (this.#center || this.#left?.blank) return
        if (this.#portrait && this.#left?.element?.style?.display === 'none') {
            this.#right.element.style.display = 'none'
            this.#left.element.style.display = 'block'
            this.#side = 'left'
            return true
        }
    }
    #goRight() {
        if (this.#center || this.#right?.blank) return
        if (this.#portrait && this.#right?.element?.style?.display === 'none') {
            this.#left.element.style.display = 'none'
            this.#right.element.style.display = 'block'
            this.#side = 'right'
            return true
        }
    }
    open(book) {
        this.book = book
        const { rendition } = book
        this.spread = rendition?.spread
        this.defaultViewport = rendition?.viewport

        const rtl = book.dir === 'rtl'
        const ltr = !rtl
        this.rtl = rtl

        if (rendition?.spread === 'none')
            this.#spreads = book.sections.map(section => ({ center: section }))
        else this.#spreads = book.sections.reduce((arr, section, i) => {
            const last = arr[arr.length - 1]
            const { pageSpread } = section
            const newSpread = () => {
                const spread = {}
                arr.push(spread)
                return spread
            }
            if (pageSpread === 'center') {
                const spread = last.left || last.right ? newSpread() : last
                spread.center = section
            }
            else if (pageSpread === 'left') {
                const spread = last.center || last.left || ltr && i ? newSpread() : last
                spread.left = section
            }
            else if (pageSpread === 'right') {
                const spread = last.center || last.right || rtl && i ? newSpread() : last
                spread.right = section
            }
            else if (ltr) {
                if (last.center || last.right) newSpread().left = section
                else if (last.left || !i) last.right = section
                else last.left = section
            }
            else {
                if (last.center || last.left) newSpread().right = section
                else if (last.right || !i) last.left = section
                else last .right = section
            }
            return arr
        }, [{}])
    }
    get index() {
        const spread = this.#spreads[this.#index]
        const section = spread?.center ?? (this.side === 'left'
            ? spread.left ?? spread.right : spread.right ?? spread.left)
        return this.book.sections.indexOf(section)
    }
    #reportLocation(reason) {
        this.dispatchEvent(new CustomEvent('relocate', { detail:
            { reason, range: null, index: this.index, fraction: 0, size: 1 } }))
    }
    getSpreadOf(section) {
        const spreads = this.#spreads
        for (let index = 0; index < spreads.length; index++) {
            const { left, right, center } = spreads[index]
            if (left === section) return { index, side: 'left' }
            if (right === section) return { index, side: 'right' }
            if (center === section) return { index, side: 'center' }
        }
    }
    async goToSpread(index, side, reason) {
        if (index < 0 || index > this.#spreads.length - 1) return
        if (index === this.#index) {
            this.#render(side)
            return
        }
        this.#index = index
        const spread = this.#spreads[index]
        if (spread.center) {
            const index = this.book.sections.indexOf(spread.center)
            const src = await spread.center?.load?.()
            await this.#showSpread({ center: { index, src } })
        } else {
            const indexL = this.book.sections.indexOf(spread.left)
            const indexR = this.book.sections.indexOf(spread.right)
            const srcL = await spread.left?.load?.()
            const srcR = await spread.right?.load?.()
            const left = { index: indexL, src: srcL }
            const right = { index: indexR, src: srcR }
            await this.#showSpread({ left, right, side })
        }
        this.#reportLocation(reason)
    }
    async select(target) {
        await this.goTo(target)
        // TODO
    }
    async goTo(target) {
        const { book } = this
        const resolved = await target
        const section = book.sections[resolved.index]
        if (!section) return
        const { index, side } = this.getSpreadOf(section)
        await this.goToSpread(index, side)
    }
    async next() {
        const s = this.rtl ? this.#goLeft() : this.#goRight()
        if (s) this.#reportLocation('page')
        else return this.goToSpread(this.#index + 1, this.rtl ? 'right' : 'left', 'page')
    }
    async prev() {
        const s = this.rtl ? this.#goRight() : this.#goLeft()
        if (s) this.#reportLocation('page')
        else return this.goToSpread(this.#index - 1, this.rtl ? 'left' : 'right', 'page')
    }
    getContents() {
        return Array.from(this.#root.querySelectorAll('iframe'), frame => ({
            doc: frame.contentDocument,
            // TODO: index, overlayer
        }))
    }
    destroy() {
        this.#observer.unobserve(this)
    }
}

customElements.define('foliate-fxl', FixedLayout)
