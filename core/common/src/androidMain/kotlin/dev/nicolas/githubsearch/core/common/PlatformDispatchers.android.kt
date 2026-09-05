package dev.nicolas.githubsearch.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val platformIoDispatcher: CoroutineDispatcher get() = Dispatchers.IO
