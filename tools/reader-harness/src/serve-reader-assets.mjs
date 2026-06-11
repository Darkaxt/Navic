import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.svg', 'image/svg+xml'],
  ['.ttf', 'font/ttf'],
  ['.otf', 'font/otf'],
  ['.wasm', 'application/wasm'],
  ['.epub', 'application/epub+zip'],
  ['.pdf', 'application/pdf'],
])

const insideRoot = (root, candidate) => {
  const relative = path.relative(root, candidate)
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative))
}

const resolveRequestPath = ({ repoRoot, requestPath, extraFiles }) => {
  const assetRoot = path.join(repoRoot, 'composeApp/src/androidMain/assets/reader')
  const fixtureRoot = path.join(repoRoot, 'tools/reader-harness/fixtures/local')
  const normalizedRequest = requestPath === '/' ? '/index.html' : requestPath
  const decodedRequest = decodeURIComponent(normalizedRequest)
  const relativeRequest = decodedRequest.replace(/^\/+/, '')

  const extraFile = extraFiles.get(`/${relativeRequest}`)
  if (extraFile) return extraFile

  if (relativeRequest.startsWith('fixtures/local/')) {
    const target = path.resolve(repoRoot, 'tools/reader-harness', relativeRequest)
    return insideRoot(fixtureRoot, target) ? target : null
  }

  const target = path.resolve(assetRoot, relativeRequest)
  return insideRoot(assetRoot, target) ? target : null
}

export const startReaderAssetServer = ({ repoRoot, port = 0, extraFiles = new Map() }) => new Promise((resolve, reject) => {
  const server = http.createServer((request, response) => {
    try {
      const url = new URL(request.url || '/', 'http://127.0.0.1')
      const filePath = resolveRequestPath({ repoRoot, requestPath: url.pathname, extraFiles })

      if (!filePath) {
        response.writeHead(403, { 'content-type': 'text/plain; charset=utf-8' })
        response.end('Forbidden')
        return
      }

      if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
        response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
        response.end('Not found')
        return
      }

      const contentType = contentTypes.get(path.extname(filePath).toLowerCase()) || 'application/octet-stream'
      response.writeHead(200, {
        'content-type': contentType,
        'cache-control': 'no-store',
      })
      fs.createReadStream(filePath).pipe(response)
    } catch (error) {
      response.writeHead(500, { 'content-type': 'text/plain; charset=utf-8' })
      response.end(error?.message || String(error))
    }
  })

  server.once('error', reject)
  server.listen(port, '127.0.0.1', () => {
    const address = server.address()
    resolve({
      origin: `http://127.0.0.1:${address.port}`,
      close: () => new Promise((closeResolve, closeReject) => {
        server.close(error => error ? closeReject(error) : closeResolve())
      }),
    })
  })
})
