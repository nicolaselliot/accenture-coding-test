plugins {
    id("githubsearch.kmp.library")
    // Generates AppConfig into commonMain. Lives here because it is the lowest module every other
    // one can already see, so no layer has to reach upward for build-time configuration.
    id("githubsearch.appconfig")
}
