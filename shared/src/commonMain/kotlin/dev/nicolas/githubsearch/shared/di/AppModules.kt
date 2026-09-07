package dev.nicolas.githubsearch.shared.di

import dev.nicolas.githubsearch.core.common.DefaultDispatcherProvider
import dev.nicolas.githubsearch.core.common.DispatcherProvider
import dev.nicolas.githubsearch.core.common.config.AppConfig
import dev.nicolas.githubsearch.core.network.GithubClientConfig
import dev.nicolas.githubsearch.core.network.configureGithubClient
import dev.nicolas.githubsearch.data.github.GithubRepository
import dev.nicolas.githubsearch.domain.GetRepositoryDetailUseCase
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.SearchRepositoriesUseCase
import dev.nicolas.githubsearch.feature.detail.DetailViewModel
import dev.nicolas.githubsearch.feature.search.SearchViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

private const val IMAGE_REQUEST_TIMEOUT_MILLIS = 15_000L
private const val IMAGE_CONNECT_TIMEOUT_MILLIS = 10_000L
private const val IMAGE_SOCKET_TIMEOUT_MILLIS = 15_000L

/**
 * The API client, as opposed to [ImageClient].
 *
 * Qualified because there are two clients and Koin resolves by type. Getting this wrong is not a
 * wiring inconvenience — see [ImageClient].
 */
internal val ApiClient = named("api")

/**
 * The image client: a plain Ktor client with none of the GitHub configuration.
 *
 * Deliberately **not** the API client. That one attaches an `Authorization` header to every
 * request, and an avatar URL points at `avatars.githubusercontent.com` — a different host from the
 * one the token was issued for. Handing the API client to Coil would send the user's PAT to every
 * host an avatar URL happens to name, which is a credential leak in exchange for saving one object.
 *
 * Still one client for the app's lifetime, which is the rule that matters: the cost being avoided
 * is a client per request, not a second purpose-built client.
 */
internal val ImageClient = named("image")

private val commonModule =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider }
        // The real clock, injected rather than called, so rate-limit reset times are fakeable.
        single<Clock> { Clock.System }
    }

private val networkModule =
    module {
        single {
            GithubClientConfig(
                baseUrl = AppConfig.BASE_URL,
                token = AppConfig.GITHUB_TOKEN,
                clock = get(),
                logLevel = AppConfig.LOG_LEVEL,
            )
        }

        // One client for the app's lifetime. No engine is named: each platform contributes its own
        // through :core:network, and Ktor resolves it. onClose, because a Ktor client owns an
        // engine, a supervisor job and a connection pool — and on Desktop an engine thread that
        // outlives the window turns "close the app" into a process that will not exit.
        single(ApiClient) { HttpClient { configureGithubClient(get()) } } withOptions { onClose { it?.close() } }

        // The same three timeouts as the API client. Without them a half-open socket to an avatar
        // host never completes: Coil's fetch suspends forever, the placeholder never resolves, and
        // the connection is held. A client with no timeout is a hang waiting to happen, and that
        // rule does not stop applying because the payload is a picture.
        single(ImageClient) {
            HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = IMAGE_REQUEST_TIMEOUT_MILLIS
                    connectTimeoutMillis = IMAGE_CONNECT_TIMEOUT_MILLIS
                    socketTimeoutMillis = IMAGE_SOCKET_TIMEOUT_MILLIS
                }
            }
        } withOptions { onClose { it?.close() } }
    }

private val dataModule =
    module {
        // Singleton, and not incidentally: GithubRepository owns the detail cache as instance
        // state, so a factory binding would give every screen an empty cache and return the detail
        // endpoint to one request per tap — against sixty an hour unauthenticated. Nothing in the
        // type system prevents that, which is why the graph test asserts it.
        single<GithubRepositoryPort> {
            GithubRepository(client = get(ApiClient), clock = get(), dispatchers = get())
        }
    }

private val domainModule =
    module {
        single { SearchRepositoriesUseCase(get()) }
        single { GetRepositoryDetailUseCase(get()) }
    }

private val featureModule =
    module {
        // The constructor DSL, not `viewModel { SearchViewModel(get(), get(), get()) }`. Koin
        // supplies SavedStateHandle through AndroidParametersHolder's parameter resolution, which
        // only the constructor form consults — a plain `get()` would look for it in the graph and
        // not find it.
        viewModelOf(::SearchViewModel)

        // Takes its coordinates as an injected parameter, passed by DetailScreen through
        // parametersOf. The route is the only source of truth for which repository this is.
        viewModel { parameters ->
            DetailViewModel(
                coordinates = parameters.get<RepositoryCoordinates>(),
                getRepositoryDetail = get(),
            )
        }
    }

/**
 * Every Koin module the application needs, in one list.
 *
 * `CLAUDE.md` asks for one Koin module per Gradle module aggregated here. They are named per source
 * module below but all declared here, because three of the project's own rules make the literal
 * arrangement impossible:
 *
 * - `:domain` may depend on `:core:common` **and nothing else**, so its use-case bindings cannot
 *   live in it.
 * - `:core:network` must never read a build property, so the [GithubClientConfig] that reads
 *   `AppConfig` cannot be wired inside it.
 * - `:shared` is the only module that may see both a port and its implementation, so the
 *   [GithubRepositoryPort] binding has to be here regardless.
 *
 * The result keeps a DI framework out of the domain, data and network layers entirely, and puts the
 * whole graph in one readable place — which is what the plan means by `:shared` owning the DI graph.
 */
internal val appModules: List<Module> =
    listOf(commonModule, networkModule, dataModule, domainModule, featureModule)
