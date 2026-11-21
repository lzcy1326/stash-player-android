package gomeng.dev.stashplayer.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.core.model.defaultStashMainTabSections
import gomeng.dev.stashplayer.core.network.StashStudioCard
import gomeng.dev.stashplayer.core.network.StashPerformerCard
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashMainTabSection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import gomeng.dev.stashplayer.core.model.withStashRandomShuffleSeed
import gomeng.dev.stashplayer.core.network.buildSimilarScenesRepository
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.components.SceneCard
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashActionPill
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashHeroMediaCard
import gomeng.dev.stashplayer.core.ui.designsystem.StashScreenHeader
import gomeng.dev.stashplayer.core.ui.designsystem.StashSectionHeader
import gomeng.dev.stashplayer.core.ui.designsystem.StashSectionHeaderModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashServerStatusCard as StashDesignServerStatusCard
import gomeng.dev.stashplayer.core.ui.designsystem.StashStatCard
import gomeng.dev.stashplayer.core.ui.designsystem.stashServerStatusCardModel
import gomeng.dev.stashplayer.feature.queue.buildQueuePlaybackRequest
import gomeng.dev.stashplayer.feature.settings.TvPairingAction
import gomeng.dev.stashplayer.feature.settings.rememberTvPairingAction
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.ui.i18n.stashString

@Composable
fun HomeRoute(
    isFoldLikeLayout: Boolean,
    profile: StashServerProfile?,
    onOpenScene: (String, List<SceneCardModel>, Boolean) -> Unit,
    onResumeScene: (String, List<SceneCardModel>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenShorts: () -> Unit,
    onOpenRecentReleases: () -> Unit,
    onOpenRecentStudios: () -> Unit,
    onOpenRecentPerformers: () -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val context = LocalContext.current
    val localRepository = remember(context) { StashLocalLibraryRepository(context) }
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val tvPairingAction = rememberTvPairingAction(profile != null, settingsRepository)
    val queueScenes by localRepository.queueScenes.collectAsState(initial = emptyList())
    val watchLaterScenes by localRepository.watchLaterScenes.collectAsState(initial = emptyList())
    val favoriteScenes by localRepository.favoriteScenes.collectAsState(initial = emptyList())
    val playbackHistoryScenes by localRepository.playbackHistoryScenes.collectAsState(initial = emptyList())
    val shortsInteractions by localRepository.shortsInteractions.collectAsState(initial = emptyList())
    var sections by remember(profile) { mutableStateOf<List<StashMainTabSection>>(emptyList()) }
    var studios by remember(profile) { mutableStateOf(emptyList<StashStudioCard>()) }
    var performers by remember(profile) { mutableStateOf(emptyList<StashPerformerCard>()) }
    var studioLoadFailed by remember(profile) { mutableStateOf(false) }
    var performerLoadFailed by remember(profile) { mutableStateOf(false) }
    var recommendedScenes by remember(profile) { mutableStateOf<List<SceneCardModel>>(emptyList()) }
    val recommendationSeed = remember(profile) { nextStashRandomSortSeed() }
    var isLoading by remember(profile) { mutableStateOf(false) }
    var error by remember(profile) { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(profile, reloadToken) {
        val activeProfile = profile
        if (activeProfile == null) {
            sections = emptyList()
            error = null
            isLoading = false
            return@LaunchedEffect
        }
        val loadStartedAtMs = System.currentTimeMillis()
        StashDebugLogBuffer.record("Home", "Server sections load started")
        isLoading = true
        error = null
        runCatching { StashGraphQlClient(activeProfile).findMainTabSections(
            defaultStashMainTabSections().filter { it.id == "recently-released-scenes" },
        ) }
            .onSuccess {
                sections = it
                StashDebugLogBuffer.record(
                    "Home",
                    "Server sections loaded in ${System.currentTimeMillis() - loadStartedAtMs}ms sections=${it.size}",
                )
            }
            .onFailure {
                if (it is CancellationException) throw it
                error = homeHubErrorText(it.message ?: it::class.simpleName) ?: stashString(R.string.auto_kr_0447)
                StashDebugLogBuffer.record("Home", "Server sections load failed", it)
            }
        isLoading = false
    }

    LaunchedEffect(profile, reloadToken) {
        val server = profile ?: return@LaunchedEffect
        runCatching { StashGraphQlClient(server).findStudioCardsPage(perPage = 12, sort = "created_at", direction = "DESC") }
            .onSuccess { studios = it.studios; studioLoadFailed = false }
            .onFailure {
                if (it is CancellationException) throw it
                studioLoadFailed = true
            }
    }

    LaunchedEffect(profile, reloadToken) {
        val server = profile ?: return@LaunchedEffect
        runCatching { StashGraphQlClient(server).findPerformerCardsPage(perPage = 12, sort = "created_at", direction = "DESC") }
            .onSuccess { performers = it.performers; performerLoadFailed = false }
            .onFailure {
                if (it is CancellationException) throw it
                performerLoadFailed = true
            }
    }

    LaunchedEffect(profile, favoriteScenes, shortsInteractions, sections, recommendationSeed) {
        val activeProfile = profile
        if (activeProfile == null) {
            recommendedScenes = emptyList()
            return@LaunchedEffect
        }
        val anchors = buildHomeRecommendationAnchors(
            favoriteScenes = favoriteScenes,
            shortsInteractions = shortsInteractions,
        )
        if (anchors.isEmpty()) {
            recommendedScenes = emptyList()
            return@LaunchedEffect
        }
        val client = StashGraphQlClient(activeProfile)
        val repository = buildSimilarScenesRepository(
            graphQlClient = client,
            stashServerProfile = activeProfile,
        )
        val hybridRecommendations = anchors
            .take(3)
            .flatMap { anchor ->
                repository.getSimilarScenes(sceneId = anchor.sceneId, limit = 12).getOrDefault(emptyList())
            }
        val libraryCandidates = runCatching {
            client.findSceneCardsPage(
                perPage = HOME_RECOMMENDATION_LIBRARY_CANDIDATE_PAGE_SIZE,
                sort = "random",
                videoFilter = StashVideoFilterState().withStashRandomShuffleSeed(recommendationSeed),
            ).scenes
        }.getOrDefault(emptyList())
        recommendedScenes = rankHomeRecommendedScenes(
            candidates = homeRecommendationCandidatePool(
                libraryCandidates = libraryCandidates,
                sectionCandidates = sections.flatMap { it.scenes },
            ),
            anchors = anchors,
            hybridRecommendations = hybridRecommendations,
        )
    }

    val hubState = buildHomeHubState(
        hasProfile = profile != null,
        serverLabel = profile?.normalizedBaseUrl().orEmpty(),
        queueCount = queueScenes.size,
        watchLaterCount = watchLaterScenes.size,
        favoriteCount = favoriteScenes.size,
        hasServerError = error != null,
    )

    fun playQueue(shuffle: Boolean) {
        val request = buildQueuePlaybackRequest(queueScenes, shuffle) ?: return
        onOpenScene(request.selectedSceneId, request.scenes, request.randomShuffle)
    }

    HomeHubContent(
        isFoldLikeLayout = isFoldLikeLayout,
        hubState = hubState,
        sections = sections,
        studios = studios,
        performers = performers,
        studioLoadFailed = studioLoadFailed,
        performerLoadFailed = performerLoadFailed,
        recommendedScenes = recommendedScenes,
        serverProfile = profile,
        queueScenes = queueScenes,
        watchLaterScenes = watchLaterScenes,
        favoriteScenes = favoriteScenes,
        playbackHistoryScenes = playbackHistoryScenes,
        isRefreshing = isLoading,
        tvPairingAction = tvPairingAction,
        error = error,
        onRefresh = { reloadToken++ },
        onOpenScene = onOpenScene,
        onResumeScene = onResumeScene,
        onOpenSettings = onOpenSettings,
        onOpenQueue = onOpenQueue,
        onOpenFavorites = onOpenFavorites,
        onOpenExplore = onOpenExplore,
        onOpenShorts = onOpenShorts,
        onOpenRecentReleases = onOpenRecentReleases,
        onOpenRecentStudios = onOpenRecentStudios,
        onOpenRecentPerformers = onOpenRecentPerformers,
        onOpenStudio = onOpenStudio,
        onOpenPerformer = onOpenPerformer,
        onPlayQueue = { playQueue(shuffle = false) },
        onShuffleQueue = { playQueue(shuffle = true) },
    )
}

private const val HOME_RECOMMENDATION_LIBRARY_CANDIDATE_PAGE_SIZE = 100

@Composable
private fun HomeHubContent(
    isFoldLikeLayout: Boolean,
    hubState: HomeHubState,
    sections: List<StashMainTabSection>,
    studios: List<StashStudioCard>,
    performers: List<StashPerformerCard>,
    studioLoadFailed: Boolean,
    performerLoadFailed: Boolean,
    recommendedScenes: List<SceneCardModel>,
    serverProfile: StashServerProfile?,
    queueScenes: List<SceneCardModel>,
    watchLaterScenes: List<SceneCardModel>,
    favoriteScenes: List<SceneCardModel>,
    playbackHistoryScenes: List<SceneCardModel>,
    isRefreshing: Boolean,
    tvPairingAction: TvPairingAction,
    error: String?,
    onRefresh: () -> Unit,
    onOpenScene: (String, List<SceneCardModel>, Boolean) -> Unit,
    onResumeScene: (String, List<SceneCardModel>) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenShorts: () -> Unit,
    onOpenRecentReleases: () -> Unit,
    onOpenRecentStudios: () -> Unit,
    onOpenRecentPerformers: () -> Unit,
    onOpenStudio: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onPlayQueue: () -> Unit,
    onShuffleQueue: () -> Unit,
) {
    val horizontalPadding = if (isFoldLikeLayout) 24.dp else 14.dp
    val hasProfile = !hubState.requiresSetup
    val serverScenes = sections.flatMap { it.scenes }
    val heroSelection = selectHomeHeroScene(
        playbackHistoryScenes = playbackHistoryScenes,
        queueScenes = queueScenes,
        watchLaterScenes = watchLaterScenes,
        serverScenes = serverScenes,
        favoriteScenes = favoriteScenes,
    )
    val heroScene = heroSelection?.scene
    val heroScenes = heroSelection?.playbackScenes.orEmpty()

    BoxWithConstraints {
        val cardWidth = ((maxWidth - horizontalPadding * 2) * 0.75f).coerceAtMost(300.dp)
        val thumbnailHeight = cardWidth * (9f / 16f)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 92.dp, top = 14.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = horizontalPadding)) {
                    StashScreenHeader(
                        title = stashString(R.string.auto_kr_0001),
                        subtitle = hubState.server.statusLabel,
                        trailing = if (hasProfile) {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val refreshLabel = stashString(
                                        if (isRefreshing) R.string.auto_kr_0448 else R.string.auto_kr_0449,
                                    )
                                    IconButton(
                                        onClick = onRefresh,
                                        enabled = !isRefreshing,
                                        modifier = Modifier.semantics { contentDescription = refreshLabel },
                                    ) {
                                        if (isRefreshing) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                                        }
                                    }
                                    IconButton(onClick = tvPairingAction.onClick, enabled = tvPairingAction.enabled) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_scan),
                                            contentDescription = stashString(R.string.tv_pairing_scan_action),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                    (tvPairingAction.progressMessage ?: tvPairingAction.resultMessage)?.let { message ->
                        Text(
                            text = stashString(message),
                            modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                }
            }

            if (!hasProfile || error != null) {
                item {
                    HomeServerStatusCard(
                        state = hubState,
                        error = error,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        onRefresh = onRefresh,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            if (hasProfile) {
                if (heroScene != null) {
                    item {
                        HomeHeroCard(
                            scene = heroScene,
                            serverProfile = serverProfile,
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                            onPlay = {
                                val scenes = heroScenes.ifEmpty { listOf(heroScene) }
                                if (heroSelection.resumesPlaybackQueue) {
                                    onResumeScene(heroScene.id, scenes)
                                } else {
                                    onOpenScene(heroScene.id, scenes, false)
                                }
                            },
                        )
                    }
                }

                item {
                    HomeMiniStatsRow(
                        queueCount = queueScenes.size,
                        watchLaterCount = watchLaterScenes.size,
                        favoriteCount = favoriteScenes.size,
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                    )
                }

                item {
                    HomeQuickActionRow(
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        canPlayQueue = queueScenes.isNotEmpty(),
                        canShuffleQueue = queueScenes.size > 1,
                        onPlayQueue = onPlayQueue,
                        onShuffleQueue = onShuffleQueue,
                        onOpenQueue = onOpenQueue,
                        onOpenFavorites = onOpenFavorites,
                        onOpenExplore = onOpenExplore,
                        onOpenShorts = onOpenShorts,
                        onOpenSettings = onOpenSettings,
                    )
                }

            }
            if (isRefreshing && sections.isEmpty() && hasProfile) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(sections, key = { it.spec.id }) { section ->
                HomeScenePreviewRow(
                    title = section.spec.title,
                    scenes = section.scenes,
                    allScenes = section.scenes,
                    cardWidth = cardWidth,
                    thumbnailHeight = thumbnailHeight,
                    horizontalPadding = horizontalPadding,
                    serverProfile = serverProfile,
                    onOpenScene = onOpenScene,
                    onViewAll = onOpenRecentReleases,
                )
            }
            if (studios.isNotEmpty() || studioLoadFailed) {
                item {
                    HomeRecentEntityRow(
                        title = stashString(R.string.home_recent_studios),
                        entries = studios,
                        key = { it.id },
                        horizontalPadding = horizontalPadding,
                        onViewAll = onOpenRecentStudios,
                        loadFailed = studioLoadFailed,
                        onRetry = onRefresh,
                    ) { studio ->
                        HomeRecentEntityCard(
                            studio.name, studio.imagePath, studio.sceneCount, 142.dp, 16f / 9f,
                            Icons.Outlined.Videocam, ContentScale.Fit, serverProfile,
                            onClick = { onOpenStudio(studio.id) },
                        )
                    }
                }
            }
            if (performers.isNotEmpty() || performerLoadFailed) {
                item {
                    HomeRecentEntityRow(
                        title = stashString(R.string.home_recent_performers),
                        entries = performers,
                        key = { it.id },
                        horizontalPadding = horizontalPadding,
                        onViewAll = onOpenRecentPerformers,
                        loadFailed = performerLoadFailed,
                        onRetry = onRefresh,
                    ) { performer ->
                        HomeRecentEntityCard(
                            performer.name, performer.imagePath, performer.sceneCount, 110.dp, 3f / 4f,
                            Icons.Outlined.Person, ContentScale.Crop, serverProfile,
                            onClick = { onOpenPerformer(performer.id) },
                        )
                    }
                }
            }

            if (hasProfile) {
                if (queueScenes.isNotEmpty()) {
                    item {
                        HomeScenePreviewRow(
                            title = homePreviewSectionTitle(HomeHubSectionId.Queue),
                            scenes = queueScenes.take(8),
                            allScenes = queueScenes,
                            cardWidth = cardWidth,
                            thumbnailHeight = thumbnailHeight,
                            horizontalPadding = horizontalPadding,
                            serverProfile = serverProfile,
                            onOpenScene = onOpenScene,
                        )
                    }
                }

                if (watchLaterScenes.isNotEmpty()) {
                    item {
                        HomeScenePreviewRow(
                            title = homePreviewSectionTitle(HomeHubSectionId.WatchLater),
                            scenes = watchLaterScenes.take(8),
                            allScenes = watchLaterScenes,
                            cardWidth = cardWidth,
                            thumbnailHeight = thumbnailHeight,
                            horizontalPadding = horizontalPadding,
                            serverProfile = serverProfile,
                            onOpenScene = onOpenScene,
                        )
                    }
                }

                if (favoriteScenes.isNotEmpty()) {
                    item {
                        HomeScenePreviewRow(
                            title = homePreviewSectionTitle(HomeHubSectionId.Favorites),
                            scenes = favoriteScenes.take(8),
                            allScenes = favoriteScenes,
                            cardWidth = cardWidth,
                            thumbnailHeight = thumbnailHeight,
                            horizontalPadding = horizontalPadding,
                            serverProfile = serverProfile,
                            onOpenScene = onOpenScene,
                        )
                    }
                }

                if (recommendedScenes.isNotEmpty()) {
                    item {
                        HomeScenePreviewRow(
                            title = homePreviewSectionTitle(HomeHubSectionId.Recommended),
                            scenes = recommendedScenes.take(8),
                            allScenes = recommendedScenes,
                            cardWidth = cardWidth,
                            thumbnailHeight = thumbnailHeight,
                            horizontalPadding = horizontalPadding,
                            serverProfile = serverProfile,
                            onOpenScene = onOpenScene,
                        )
                    }
                }

            }

            if (hasProfile && !isRefreshing && sections.isEmpty() && error == null) {
                item {
                    StashEmptyState(
                        state = StashEmptyStateModel(
                            title = stashString(R.string.auto_kr_0452),
                            message = stashString(R.string.auto_kr_0453),
                            primaryActionLabel = stashString(R.string.auto_kr_0002),
                        ),
                        modifier = Modifier.padding(horizontal = horizontalPadding),
                        onPrimaryAction = onOpenExplore,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    scene: SceneCardModel,
    serverProfile: StashServerProfile?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
) {
    val thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, serverProfile)
    StashHeroMediaCard(
        title = scene.title,
        subtitle = scene.subtitle.ifBlank { stashString(R.string.auto_kr_0383) },
        thumbnailModel = thumbnailModel,
        thumbnailContentDescription = scene.title,
        metadataLabels = scene.metadataBadges.map { it.label },
        showResumeBadge = scene.progress > 0f,
        primaryActionLabel = stashString(R.string.auto_kr_0039),
        onPrimaryAction = onPlay,
        modifier = modifier.height(360.dp),
    )
}

@Composable
private fun HomeMiniStatsRow(
    queueCount: Int,
    watchLaterCount: Int,
    favoriteCount: Int,
    modifier: Modifier = Modifier,
) {
    val stats = buildHomeDashboardStats(
        queueCount = queueCount,
        watchLaterCount = watchLaterCount,
        favoriteCount = favoriteCount,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.forEach { stat ->
            StashStatCard(
                label = stat.label,
                value = stat.value,
                subtitle = stat.subtitle,
                contentDescription = stashString(R.string.auto_kr_0454, stat.label, stat.value),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeQuickActionRow(
    modifier: Modifier = Modifier,
    canPlayQueue: Boolean,
    canShuffleQueue: Boolean,
    onPlayQueue: () -> Unit,
    onShuffleQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenShorts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val actions = buildHomeQuickActions(
        canPlayQueue = canPlayQueue,
        canShuffleQueue = canShuffleQueue,
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(actions, key = { it.key }) { action ->
            StashActionPill(
                label = action.label,
                enabled = action.enabled,
                onClick = {
                    when (action.action) {
                        HomeHubAction.PlayQueue -> onPlayQueue()
                        HomeHubAction.ShuffleQueue -> onShuffleQueue()
                        HomeHubAction.OpenQueue -> onOpenQueue()
                        HomeHubAction.OpenFavorites -> onOpenFavorites()
                        HomeHubAction.OpenExplore -> onOpenExplore()
                        HomeHubAction.OpenShorts -> onOpenShorts()
                        HomeHubAction.OpenBrowse -> onOpenExplore()
                        HomeHubAction.OpenSettings -> onOpenSettings()
                        HomeHubAction.RetryServerSections -> Unit
                    }
                },
                icon = {
                    Icon(
                        imageVector = homeQuickActionIcon(action),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

private fun homeQuickActionIcon(action: HomeQuickActionModel): ImageVector = when (action.key) {
    HomeQuickActionKey.PlayQueue -> Icons.Outlined.PlayArrow
    HomeQuickActionKey.ShuffleQueue -> Icons.Outlined.Shuffle
    HomeQuickActionKey.OpenQueue -> Icons.AutoMirrored.Outlined.PlaylistPlay
    HomeQuickActionKey.OpenWatchLater -> Icons.Outlined.Bookmarks
    HomeQuickActionKey.OpenFavorites -> Icons.Outlined.Star
    HomeQuickActionKey.OpenExplore -> Icons.Outlined.Explore
    HomeQuickActionKey.OpenShorts -> Icons.Outlined.PlayArrow
    HomeQuickActionKey.OpenSettings -> Icons.Outlined.Settings
}

@Composable
private fun HomeServerStatusCard(
    state: HomeHubState,
    error: String?,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val model = stashServerStatusCardModel(
        statusLabel = state.server.statusLabel,
        detail = state.server.detail,
        requiresSetup = state.requiresSetup,
        hasError = error != null,
        errorMessage = error,
    )
    StashDesignServerStatusCard(
        model = model,
        onPrimaryAction = when {
            state.requiresSetup -> onOpenSettings
            error != null -> onRefresh
            else -> null
        },
        onSecondaryAction = if (error != null) onOpenSettings else null,
        modifier = modifier,
    )
}

@Composable
private fun HomeScenePreviewRow(
    title: String,
    scenes: List<SceneCardModel>,
    allScenes: List<SceneCardModel>,
    cardWidth: androidx.compose.ui.unit.Dp,
    thumbnailHeight: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    serverProfile: StashServerProfile?,
    onOpenScene: (String, List<SceneCardModel>, Boolean) -> Unit,
    onViewAll: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StashSectionHeader(
            state = StashSectionHeaderModel(
                title = title,
                itemCount = allScenes.size,
            ),
            modifier = Modifier.padding(horizontal = horizontalPadding),
            onActionClick = onViewAll,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
        ) {
            items(scenes, key = { it.id }) { scene ->
                val thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, serverProfile)
                SceneCard(
                    scene = scene,
                    onClick = { onOpenScene(scene.id, allScenes, false) },
                    modifier = Modifier.width(cardWidth),
                    thumbnailHeight = thumbnailHeight,
                    prioritizeStudioCode = true,
                    thumbnailModel = thumbnailModel,
                )
            }
        }
    }
}


@Composable
private fun <T> HomeRecentEntityRow(
    title: String,
    entries: List<T>,
    key: (T) -> String,
    horizontalPadding: Dp,
    onViewAll: () -> Unit,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    card: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StashSectionHeader(
            state = StashSectionHeaderModel(
                title = title,
                actionLabel = stashString(if (loadFailed) R.string.home_retry else R.string.home_view_all),
            ),
            modifier = Modifier.padding(horizontal = horizontalPadding),
            onActionClick = if (loadFailed) onRetry else onViewAll,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
        ) {
            items(entries, key = key) { card(it) }
        }
    }
}

@Composable
private fun HomeRecentEntityCard(
    name: String,
    imagePath: String?,
    sceneCount: Int,
    width: Dp,
    aspectRatio: Float,
    fallbackIcon: ImageVector,
    contentScale: ContentScale,
    profile: StashServerProfile?,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center,
        ) {
            val imageModel = rememberStashThumbnailModel(imagePath, profile)
            if (imageModel == null) {
                Icon(fallbackIcon, contentDescription = null)
            } else {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(if (contentScale == ContentScale.Fit) 8.dp else 0.dp),
                    contentScale = contentScale,
                    error = androidx.compose.ui.graphics.vector.rememberVectorPainter(fallbackIcon),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sceneCount > 0) {
                Text(
                    stashString(R.string.performers_scene_count, sceneCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
