package paige.navic.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AlbumRepository
import paige.navic.domain.repositories.ArtistRepository
import paige.navic.domain.repositories.AurralMetadataCache
import paige.navic.domain.repositories.AurralArtistCreditLookup
import paige.navic.domain.repositories.BinderyMetadataCache
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.CollectionRepository
import paige.navic.domain.repositories.DbRepository
import paige.navic.domain.repositories.GenreRepository
import paige.navic.domain.repositories.LastFmRepository
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.LyricsRepository
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.domain.repositories.PlaylistRepository
import paige.navic.domain.repositories.RadioRepository
import paige.navic.domain.repositories.RoomAurralMetadataCache
import paige.navic.domain.repositories.RoomBinderyMetadataCache
import paige.navic.domain.repositories.SearchRepository
import paige.navic.domain.repositories.ShareRepository
import paige.navic.domain.repositories.SongRepository
import paige.navic.domain.repositories.ArtistCreditLookup
import paige.navic.domain.repositories.ArtistCreditResolutionRepository

val repositoryModule = module {
	singleOf(::AlbumRepository)
	singleOf(::ArtistRepository)
	singleOf(::DbRepository)
	singleOf(::GenreRepository)
	single { LastFmRepository(get()) }
	single<AurralMetadataCache> { RoomAurralMetadataCache(get()) }
	single<BinderyMetadataCache> { RoomBinderyMetadataCache(get()) }
	single { BinderyRepository(preferenceManager = get(), metadataCache = get()) }
	single { LidaClipsRepository(get()) }
	single { AurralRepository(preferenceManager = get(), metadataCache = get(), confirmationWorkerEnabled = true) }
	single<ArtistCreditLookup> { AurralArtistCreditLookup(get()) }
	single { ArtistCreditResolutionRepository(metadataCache = get(), lookup = get()) }
	singleOf(::LyricsRepository)
	singleOf(::MusicBrainzArtworkRepository)
	single { PlaybackOriginRepository(get()) }
	singleOf(::SearchRepository)
	singleOf(::ShareRepository)
	singleOf(::CollectionRepository)
	singleOf(::PlaylistRepository)
	singleOf(::SongRepository)
	singleOf(::RadioRepository)
}
