package com.statix.android.systemui.statusbar.phone;

import static com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME;

import android.app.WallpaperManager;
import android.content.Context;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Handler;
import android.os.PowerManager;
import android.service.dreams.IDreamManager;
import android.util.DisplayMetrics;

import com.android.app.viewcapture.ViewCaptureAwareWindowManager;
import com.android.internal.logging.MetricsLogger;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.InitController;
import com.android.systemui.accessibility.floatingmenu.AccessibilityFloatingMenuController;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.assist.AssistManager;
import com.android.systemui.back.domain.interactor.BackActionInteractor;
import com.android.systemui.biometrics.AuthRippleController;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.charging.WiredChargingRippleController;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.communal.domain.interactor.CommunalInteractor;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.demomode.DemoModeController;
import com.android.systemui.emergency.EmergencyGestureModule.EmergencyGestureIntentFactory;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.fragments.FragmentService;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.keyguard.ui.viewmodel.LightRevealScrimViewModel;
import com.android.systemui.navigationbar.NavigationBarController;
import com.android.systemui.notetask.NoteTaskController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.PluginDependencyProvider;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.scene.domain.interactor.WindowRootViewVisibilityInteractor;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.settings.brightness.BrightnessSliderController;
import com.android.systemui.settings.brightness.domain.interactor.BrightnessMirrorShowingInteractor;
import com.android.systemui.shade.CameraLauncher;
import com.android.systemui.shade.GlanceableHubContainerController;
import com.android.systemui.shade.NotificationShadeWindowViewController;
import com.android.systemui.shade.QuickSettingsController;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.ShadeLogger;
import com.android.systemui.shade.ShadeSurface;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.LightRevealScrim;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.NotificationPresenter;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.core.StatusBarInitializer;
import com.android.systemui.statusbar.data.repository.StatusBarModeRepositoryStore;
import com.android.systemui.statusbar.notification.NotificationActivityStarter;
import com.android.systemui.statusbar.notification.NotificationLaunchAnimatorControllerProvider;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.init.NotificationsController;
import com.android.systemui.statusbar.notification.row.NotificationGutsManager;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks;
import com.android.systemui.statusbar.phone.CentralSurfacesImpl;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.DozeScrimController;
import com.android.systemui.statusbar.phone.DozeServiceHost;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.StatusBarHideIconsForBouncerManager;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.StatusBarTouchableRegionManager;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.HeadsUpManager;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.UserInfoControllerImpl;
import com.android.systemui.statusbar.window.StatusBarWindowController;
import com.android.systemui.statusbar.window.StatusBarWindowStateController;
import com.android.systemui.util.WallpaperController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.concurrency.MessageRouter;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.android.systemui.volume.VolumeComponent;
import com.android.wm.shell.bubbles.Bubbles;
import com.android.wm.shell.startingsurface.StartingSurface;

import com.statix.android.systemui.statusbar.KeyguardIndicationControllerStatix;

import dagger.Lazy;

import java.util.Optional;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

@SysUISingleton
public class StatixCentralSurfacesImpl extends CentralSurfacesImpl {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Inject
    public StatixCentralSurfacesImpl(
            Context context,
            NotificationsController notificationsController,
            FragmentService fragmentService,
            LightBarController lightBarController,
            AutoHideController autoHideController,
            StatusBarInitializer statusBarInitializer,
            StatusBarWindowController statusBarWindowController,
            StatusBarWindowStateController statusBarWindowStateController,
            StatusBarModeRepositoryStore statusBarModeRepository,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            StatusBarSignalPolicy statusBarSignalPolicy,
            PulseExpansionHandler pulseExpansionHandler,
            NotificationWakeUpCoordinator notificationWakeUpCoordinator,
            KeyguardBypassController keyguardBypassController,
            KeyguardStateController keyguardStateController,
            HeadsUpManager headsUpManager,
            FalsingManager falsingManager,
            FalsingCollector falsingCollector,
            BroadcastDispatcher broadcastDispatcher,
            NotificationGutsManager notificationGutsManager,
            ShadeExpansionStateManager shadeExpansionStateManager,
            KeyguardViewMediator keyguardViewMediator,
            DisplayMetrics displayMetrics,
            MetricsLogger metricsLogger,
            ShadeLogger shadeLogger,
            JavaAdapter javaAdapter,
            @UiBackground Executor uiBgExecutor,
            ShadeSurface shadeSurface,
            NotificationMediaManager notificationMediaManager,
            NotificationLockscreenUserManager lockScreenUserManager,
            NotificationRemoteInputManager remoteInputManager,
            QuickSettingsController quickSettingsController,
            BatteryController batteryController,
            SysuiColorExtractor colorExtractor,
            ScreenLifecycle screenLifecycle,
            WakefulnessLifecycle wakefulnessLifecycle,
            PowerInteractor powerInteractor,
            CommunalInteractor communalInteractor,
            SysuiStatusBarStateController statusBarStateController,
            Optional<Bubbles> bubblesOptional,
            Lazy<NoteTaskController> noteTaskControllerLazy,
            DeviceProvisionedController deviceProvisionedController,
            NavigationBarController navigationBarController,
            AccessibilityFloatingMenuController accessibilityFloatingMenuController,
            Lazy<AssistManager> assistManagerLazy,
            ConfigurationController configurationController,
            NotificationShadeWindowController notificationShadeWindowController,
            Lazy<NotificationShadeWindowViewController> notificationShadeWindowViewControllerLazy,
            NotificationStackScrollLayoutController notificationStackScrollLayoutController,
            // Lazys due to b/298099682.
            Lazy<NotificationPresenter> notificationPresenterLazy,
            Lazy<NotificationActivityStarter> notificationActivityStarterLazy,
            NotificationLaunchAnimatorControllerProvider notifTransitionAnimatorControllerProvider,
            DozeParameters dozeParameters,
            ScrimController scrimController,
            Lazy<BiometricUnlockController> biometricUnlockControllerLazy,
            AuthRippleController authRippleController,
            DozeServiceHost dozeServiceHost,
            BackActionInteractor backActionInteractor,
            PowerManager powerManager,
            DozeScrimController dozeScrimController,
            VolumeComponent volumeComponent,
            CommandQueue commandQueue,
            Lazy<CentralSurfacesCommandQueueCallbacks> commandQueueCallbacksLazy,
            PluginManager pluginManager,
            ShadeController shadeController,
            WindowRootViewVisibilityInteractor windowRootViewVisibilityInteractor,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            ViewMediatorCallback viewMediatorCallback,
            InitController initController,
            @Named(TIME_TICK_HANDLER_NAME) Handler timeTickHandler,
            PluginDependencyProvider pluginDependencyProvider,
            ExtensionController extensionController,
            UserInfoControllerImpl userInfoControllerImpl,
            PhoneStatusBarPolicy phoneStatusBarPolicy,
            KeyguardIndicationControllerStatix keyguardIndicationController,
            DemoModeController demoModeController,
            Lazy<NotificationShadeDepthController> notificationShadeDepthControllerLazy,
            StatusBarTouchableRegionManager statusBarTouchableRegionManager,
            BrightnessSliderController.Factory brightnessSliderFactory,
            ScreenOffAnimationController screenOffAnimationController,
            WallpaperController wallpaperController,
            StatusBarHideIconsForBouncerManager statusBarHideIconsForBouncerManager,
            LockscreenShadeTransitionController lockscreenShadeTransitionController,
            FeatureFlags featureFlags,
            KeyguardUnlockAnimationController keyguardUnlockAnimationController,
            @Main DelayableExecutor delayableExecutor,
            @Main MessageRouter messageRouter,
            WallpaperManager wallpaperManager,
            Optional<StartingSurface> startingSurfaceOptional,
            ActivityTransitionAnimator activityTransitionAnimator,
            DeviceStateManager deviceStateManager,
            WiredChargingRippleController wiredChargingRippleController,
            IDreamManager dreamManager,
            Lazy<CameraLauncher> cameraLauncherLazy,
            Lazy<LightRevealScrimViewModel> lightRevealScrimViewModelLazy,
            LightRevealScrim lightRevealScrim,
            AlternateBouncerInteractor alternateBouncerInteractor,
            UserTracker userTracker,
            Provider<FingerprintManager> fingerprintManager,
            ActivityStarter activityStarter,
            BrightnessMirrorShowingInteractor brightnessMirrorShowingInteractor,
            GlanceableHubContainerController glanceableHubContainerController,
            EmergencyGestureIntentFactory emergencyGestureIntentFactory,
            ViewCaptureAwareWindowManager viewCaptureAwareWindowManager) {
        super(
                context,
                notificationsController,
                fragmentService,
                lightBarController,
                autoHideController,
                statusBarInitializer,
                statusBarWindowController,
                statusBarWindowStateController,
                statusBarModeRepository,
                keyguardUpdateMonitor,
                statusBarSignalPolicy,
                pulseExpansionHandler,
                notificationWakeUpCoordinator,
                keyguardBypassController,
                keyguardStateController,
                headsUpManager,
                falsingManager,
                falsingCollector,
                broadcastDispatcher,
                notificationGutsManager,
                shadeExpansionStateManager,
                keyguardViewMediator,
                displayMetrics,
                metricsLogger,
                shadeLogger,
                javaAdapter,
                uiBgExecutor,
                shadeSurface,
                notificationMediaManager,
                lockScreenUserManager,
                remoteInputManager,
                quickSettingsController,
                batteryController,
                colorExtractor,
                screenLifecycle,
                wakefulnessLifecycle,
                powerInteractor,
                communalInteractor,
                statusBarStateController,
                bubblesOptional,
                noteTaskControllerLazy,
                deviceProvisionedController,
                navigationBarController,
                accessibilityFloatingMenuController,
                assistManagerLazy,
                configurationController,
                notificationShadeWindowController,
                notificationShadeWindowViewControllerLazy,
                notificationStackScrollLayoutController,
                notificationPresenterLazy,
                notificationActivityStarterLazy,
                notifTransitionAnimatorControllerProvider,
                dozeParameters,
                scrimController,
                biometricUnlockControllerLazy,
                authRippleController,
                dozeServiceHost,
                backActionInteractor,
                powerManager,
                dozeScrimController,
                volumeComponent,
                commandQueue,
                commandQueueCallbacksLazy,
                pluginManager,
                shadeController,
                windowRootViewVisibilityInteractor,
                statusBarKeyguardViewManager,
                viewMediatorCallback,
                initController,
                timeTickHandler,
                pluginDependencyProvider,
                extensionController,
                userInfoControllerImpl,
                phoneStatusBarPolicy,
                keyguardIndicationController,
                demoModeController,
                notificationShadeDepthControllerLazy,
                statusBarTouchableRegionManager,
                brightnessSliderFactory,
                screenOffAnimationController,
                wallpaperController,
                statusBarHideIconsForBouncerManager,
                lockscreenShadeTransitionController,
                featureFlags,
                keyguardUnlockAnimationController,
                delayableExecutor,
                messageRouter,
                wallpaperManager,
                startingSurfaceOptional,
                activityTransitionAnimator,
                deviceStateManager,
                wiredChargingRippleController,
                dreamManager,
                cameraLauncherLazy,
                lightRevealScrimViewModelLazy,
                lightRevealScrim,
                alternateBouncerInteractor,
                userTracker,
                fingerprintManager,
                activityStarter,
                brightnessMirrorShowingInteractor,
                glanceableHubContainerController,
                emergencyGestureIntentFactory,
                viewCaptureAwareWindowManager);
    }
}
