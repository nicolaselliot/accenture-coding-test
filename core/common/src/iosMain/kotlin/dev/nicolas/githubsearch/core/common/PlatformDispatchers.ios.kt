package dev.nicolas.githubsearch.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

// `IO` is imported explicitly because on Kotlin/Native it is an extension property on Dispatchers
// rather than a member, so unqualified `Dispatchers.IO` resolves to the internal backing field and
// fails to compile. The dispatcher itself is the same elastic pool the JVM gets.
internal actual val platformIoDispatcher: CoroutineDispatcher get() = Dispatchers.IO
