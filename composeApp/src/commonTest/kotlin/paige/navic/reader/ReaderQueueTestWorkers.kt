package paige.navic.reader

/** Real parallel workers on each target, with bounded completion and no exception payload logging. */
internal expect fun runReaderQueueWorkers(workers: Int = 4, block: (Int) -> Unit)
