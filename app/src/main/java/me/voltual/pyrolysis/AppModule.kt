//Copyright (C) 2025 Voltual
// 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证第3版
//（或任意更新的版本）的条款重新分发和/或修改它。
//本程序是基于希望它有用而分发的，但没有任何担保；甚至没有适销性或特定用途适用性的隐含担保。
// 有关更多细节，请参阅 GNU 通用公共许可证。
//
// 你应该已经收到了一份 GNU 通用公共许可证的副本
// 如果没有，请查阅 <http://www.gnu.org/licenses/>.
package me.voltual.pyrolysis

import me.voltual.pyrolysis.core.database.*
import me.voltual.pyrolysis.core.database.dao.*
import me.voltual.pyrolysis.ui.auth.LoginViewModel
import me.voltual.pyrolysis.ui.billing.BillingViewModel
import org.koin.android.ext.koin.androidContext
import me.voltual.pyrolysis.ui.community.CommunityViewModel
import me.voltual.pyrolysis.ui.community.FollowingPostsViewModel
import me.voltual.pyrolysis.ui.community.HotPostsViewModel
import me.voltual.pyrolysis.data.DeviceNameDataStore
import me.voltual.pyrolysis.ui.user.UserProfileViewModel
import me.voltual.pyrolysis.ui.community.MyLikesViewModel
import me.voltual.pyrolysis.ui.payment.PaymentViewModel
import me.voltual.pyrolysis.ui.user.MyReviewsViewModel
import me.voltual.pyrolysis.feature.store.worker.workmanagerModule
import me.voltual.pyrolysis.ui.log.LogViewModel
import me.voltual.pyrolysis.ui.user.UserListViewModel
import me.voltual.pyrolysis.ui.settings.PrefsVM
import me.voltual.pyrolysis.ui.message.MessageViewModel
import me.voltual.pyrolysis.ui.community.PostCreateViewModel
import me.voltual.pyrolysis.ui.plaza.*
import me.voltual.pyrolysis.ui.player.PlayerViewModel
import me.voltual.pyrolysis.ui.settings.signin.SignInSettingsViewModel
import me.voltual.pyrolysis.ui.settings.repos.RepoPageVM
import me.voltual.pyrolysis.ui.search.SearchViewModel
import me.voltual.pyrolysis.ui.user.MyPostsViewModel
import me.voltual.pyrolysis.ui.user.UserDetailViewModel
import me.voltual.pyrolysis.ui.settings.storage.StoreManagerViewModel 
import me.voltual.pyrolysis.data.StorageSettingsDataStore 
import me.voltual.pyrolysis.data.SearchHistoryDataStore
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import me.voltual.pyrolysis.ui.community.BrowseHistoryViewModel
import me.voltual.pyrolysis.ui.community.PostDetailViewModel
import me.voltual.pyrolysis.ui.rank.RankingListViewModel
import me.voltual.pyrolysis.ui.settings.update.UpdateSettingsViewModel
import me.voltual.pyrolysis.ui.home.HomeViewModel
import me.voltual.pyrolysis.data.UserFilterDataStore
import me.voltual.pyrolysis.data.UserAgreementDataStore
import me.voltual.pyrolysis.ui.user.MyCommentsViewModel
import me.voltual.pyrolysis.feature.store.repository.*

val appModule = module {
    // ViewModel definitions
    viewModel { LoginViewModel(androidApplication()) }
    viewModel { BillingViewModel(androidApplication()) }
    viewModel { CommunityViewModel() }
    viewModel { FollowingPostsViewModel(androidApplication()) }
    viewModel { HotPostsViewModel() }
    viewModel { MyLikesViewModel(androidApplication()) }
    viewModel { LogViewModel(androidApplication()) }
    viewModel { MessageViewModel(androidApplication()) }   
    viewModel { AppDetailComposeViewModel(androidApplication(), get()) }
    
    viewModel { AppReleaseViewModel(androidApplication()) }
    
    viewModel { PlazaViewModel(androidApplication(),get(),get(), get(), get()) }
    
    viewModel { PlayerViewModel(androidApplication()) }
    
    viewModel { SearchViewModel(get(), get()) }
    
    viewModel { UserListViewModel(androidApplication()) }
    viewModel { PostCreateViewModel(androidApplication()) }
    viewModel { MyPostsViewModel(get()) }
    viewModel { PaymentViewModel(androidApplication()) }
    viewModel { VersionListViewModel(androidApplication(), get()) }
    viewModel { UserDetailViewModel(androidApplication()) }
    viewModel { StoreManagerViewModel(androidApplication()) }
    
    viewModel { BrowseHistoryViewModel(androidApplication()) }
    viewModel { PostDetailViewModel(androidApplication()) }
    viewModel { RankingListViewModel() }
    viewModel { UpdateSettingsViewModel() }
    viewModel { SignInSettingsViewModel() }
    viewModel { HomeViewModel() }
    viewModel { MyCommentsViewModel(androidApplication(), get()) }
    viewModel { MyReviewsViewModel(androidApplication(), get()) }
    viewModel { RepoPageVM(get(), get()) }
    viewModel { PrefsVM(get(), get(), get()) }
    viewModel { SearchVM(get(), get(), get()) }    
    viewModel { ExploreVM(get(), get(), get()) }
    viewModel { AppPageVM(get(), get(), get(),get(),get(),get()) }

    // Singletons    
    single { UserFilterDataStore(get()) }    
    single { InstallsRepository(get()) }  
    single { ExtrasRepository(get()) }    
    single { DownloadedRepository(get()) }    
    single { ProductsRepository(get(), get(), get(), get()) }
    single { PrivacyRepository(get(), get(), get(),get(), get()) }
    single { InstalledRepository(get(), get()) }
    single { RepositoriesRepository(get(), get(), get()) }
    single { UserAgreementDataStore(androidContext()) }    
    single { BBQApplication.instance.database }
    single { get<AppDatabase>().logDao() }  
    single { get<AppDatabase>().browseHistoryDao() } 
    single { get<AppDatabase>().networkCacheDao() }  
    single { get<AppDatabase>().postDraftDao() }         
    single { SearchHistoryDataStore(androidApplication()) }
    single { StorageSettingsDataStore(androidApplication()) }
    viewModel { UserProfileViewModel(get(), get()) }
    
    single { DeviceNameDataStore(androidContext()) }
    single { XiaoQuRepository(KtorClient.ApiServiceImpl) }
    single { SineShopRepository() }
    single { SineOpenMarketRepository() } 
    single { LingMarketRepository() }    
    single<Map<AppStore, IAppStoreRepository>> {
    val map = mutableMapOf<AppStore, IAppStoreRepository>()
    map[AppStore.XIAOQU_SPACE] = get<XiaoQuRepository>()
    map[AppStore.SIENE_SHOP] = get<SineShopRepository>()
    map[AppStore.SINE_OPEN_MARKET] = get<SineOpenMarketRepository>()
    map[AppStore.LING_MARKET] = get<LingMarketRepository>()
    map
}
}