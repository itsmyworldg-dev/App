package com.example.web

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class ThikanaWebViewClient(
    private val context: Context,
    private val onPageLoadStarted: (url: String?) -> Unit,
    private val onPageLoadFinished: (url: String?) -> Unit,
    private val onReceivedLoadError: (String) -> Unit,
    private val onUrlChanged: ((url: String?) -> Unit)? = null,
    private val onRenderProcessCrashed: (() -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // Sub-resources (images, fonts, scripts, iframes, stylesheets) must NEVER be intercepted
        if (request?.isForMainFrame == false) {
            return false
        }

        val url = request?.url?.toString() ?: return false
        val uri = request.url

        // Notify URL change early so dock can be hidden instantly before page load finishes
        if (!isThikanaUrl(url)) {
            onUrlChanged?.invoke(url)
        }

        // Handle Google Maps & Map Navigation URLs natively
        if (url.startsWith("https://www.google.com/maps") ||
            url.startsWith("http://www.google.com/maps") ||
            url.startsWith("https://maps.google.com") ||
            url.startsWith("http://maps.google.com") ||
            url.startsWith("geo:")
        ) {
            return try {
                val dest = uri.getQueryParameter("destination")
                val gmmIntentUri = if (!dest.isNullOrBlank()) {
                    Uri.parse("google.navigation:q=$dest&mode=d")
                } else {
                    uri
                }
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                } else {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                }
                true
            } catch (e: Exception) {
                Log.e("ThikanaWebClient", "Error launching external maps", e)
                false
            }
        }

        // Handle external non-web schemes and communication apps
        if (url.startsWith("tel:") ||
            url.startsWith("mailto:") ||
            url.startsWith("sms:") ||
            url.startsWith("whatsapp:") ||
            url.contains("wa.me/") ||
            url.startsWith("intent:") ||
            url.startsWith("market:")
        ) {
            return try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e("ThikanaWebClient", "Error opening external app for URL: $url", e)
                false
            }
        }

        // Allow all web navigation to proceed within the WebView
        return false
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        Log.w("ThikanaWebClient", "SSL error encountered: $error. Continuing page load...")
        // In streaming emulator and production hybrid webviews, proceed to prevent silent blank screens
        handler?.proceed()
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        val currentUrl = url ?: view?.url
        onUrlChanged?.invoke(currentUrl)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d("ThikanaWebClient", "Page loading started: $url")
        val currentUrl = url ?: view?.url
        onUrlChanged?.invoke(currentUrl)
        onPageLoadStarted(currentUrl)

        if (isThikanaUrl(currentUrl)) {
            // Early polyfill of SpeechSynthesis so that web app's speak() and voice detection
            // immediately binds to native Android TextToSpeech for turn-by-turn guidance
            val speechPolyfillJs = """
                (function() {
                    try {
                        if (window.__speechSynthPolyfilled) return;
                        window.__speechSynthPolyfilled = true;

                        var navNativeSynth = {
                            speaking: false,
                            paused: false,
                            pending: false,
                            onvoiceschanged: null,
                            getVoices: function() {
                                return [
                                    { lang: 'en-IN', name: 'Mera Thikaana Indian English', default: true },
                                    { lang: 'hi-IN', name: 'Mera Thikaana Hindi', default: false }
                                ];
                            },
                            speak: function(u) {
                                var text = (u && u.text) ? u.text : String(u || '');
                                if (text && window.AndroidNativeAuth && window.AndroidNativeAuth.speakTurnByTurn) {
                                    window.AndroidNativeAuth.speakTurnByTurn(text, '');
                                }
                            },
                            cancel: function() {
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.stopSpeakingNav) {
                                    window.AndroidNativeAuth.stopSpeakingNav();
                                }
                            },
                            pause: function() {},
                            resume: function() {}
                        };

                        try {
                            Object.defineProperty(window, 'speechSynthesis', {
                                get: function() { return navNativeSynth; },
                                configurable: true
                            });
                        } catch(e) {
                            window.speechSynthesis = navNativeSynth;
                        }

                        if (typeof window.SpeechSynthesisUtterance === 'undefined') {
                            window.SpeechSynthesisUtterance = function(t) {
                                this.text = t || '';
                                this.lang = 'en-IN';
                                this.rate = 1.0;
                                this.pitch = 1.0;
                            };
                        }
                    } catch(e) {}
                })();
            """.trimIndent()
            view?.evaluateJavascript(speechPolyfillJs, null)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        val currentUrl = url ?: view?.url ?: ""
        onUrlChanged?.invoke(currentUrl)
        if (!isThikanaUrl(currentUrl)) return

        // Inject early CSS once page rendering starts so floating dock has seamless layout space.
        // Use textContent instead of innerHTML to satisfy TrustedHTML / Trusted Types policies.
        val earlyInitJs = """
            (function() {
                try {
                    if (document.getElementById('native-bar-style')) return;
                    var target = document.head || document.documentElement || document.body;
                    if (target) {
                        var style = document.createElement('style');
                        style.id = 'native-bar-style';
                        style.textContent = `$DOCK_OVERLAY_CSS`;
                        target.appendChild(style);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(earlyInitJs, null)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d("ThikanaWebClient", "Page loading finished: $url")
        val currentUrl = url ?: view?.url ?: ""
        onUrlChanged?.invoke(currentUrl)
        onPageLoadFinished(currentUrl)

        // Ensure session cookies are flushed to disk for background notification & message sync
        try {
            val cookie = CookieManager.getInstance().getCookie("https://thikana.pages.dev")
            if (!cookie.isNullOrBlank()) {
                view?.context?.let { ctx ->
                    com.example.notifications.ThikanaNotificationSyncManager.saveSessionCookie(ctx, cookie)
                }
            }
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}

        if (!isThikanaUrl(currentUrl)) return

        // Inject bridge hook for layout space, smooth 60fps sensor bridge, haptics, and navigation.
        // Use textContent instead of innerHTML to satisfy TrustedHTML / Trusted Types policies.
        val bridgeJs = """
            (function() {
                try {
                    // 1. Maintain layout space for native floating dock while keeping web navbar invisible
                    if (!document.getElementById('native-bar-style')) {
                        var target = document.head || document.documentElement || document.body;
                        if (target) {
                            var style = document.createElement('style');
                            style.id = 'native-bar-style';
                            style.textContent = `$DOCK_OVERLAY_CSS`;
                            target.appendChild(style);
                        }
                    }
                } catch(e) {}

                if (window.__nativeThikanaBridgeInjected) {
                    return;
                }
                window.__nativeThikanaBridgeInjected = true;

                // 2. Hook window.showView, modal functions, and watch active view/overlays to notify native Compose navigation bar
                var lastReportedView = '';
                function notifyView(v) {
                    if (v && v !== lastReportedView && window.AndroidNativeAuth && window.AndroidNativeAuth.onWebViewChanged) {
                        lastReportedView = v;
                        window.AndroidNativeAuth.onWebViewChanged(v);
                    }
                }

                var origShowView = window.showView;
                if (typeof origShowView === 'function') {
                    window.showView = function(viewName) {
                        origShowView.apply(this, arguments);
                        notifyView(viewName);
                    };
                }

                // Explicitly wrap openDetail and closeDetail so clicking any card hides the dock with 0ms lag
                var origOpenDetail = window.openDetail;
                if (typeof origOpenDetail === 'function') {
                    window.openDetail = function() {
                        notifyView('detail');
                        return origOpenDetail.apply(this, arguments);
                    };
                }
                var origCloseDetail = window.closeDetail;
                if (typeof origCloseDetail === 'function') {
                    window.closeDetail = function() {
                        var ret = origCloseDetail.apply(this, arguments);
                        setTimeout(scheduleCheckActiveView, 60);
                        return ret;
                    };
                }

                // Hook pushUIModal / closeUIModal
                var origPushUIModal = window.pushUIModal;
                if (typeof origPushUIModal === 'function') {
                    window.pushUIModal = function(kind) {
                        if (kind && kind !== 'uploadChooser') {
                            if (kind === 'msgthread') {
                                notifyView('chat');
                            } else {
                                notifyView(kind);
                            }
                        }
                        return origPushUIModal.apply(this, arguments);
                    };
                }
                var origCloseUIModal = window.closeUIModal;
                if (typeof origCloseUIModal === 'function') {
                    window.closeUIModal = function(kind) {
                        var ret = origCloseUIModal.apply(this, arguments);
                        if (kind === 'msgthread') {
                            notifyView('messages');
                        }
                        setTimeout(scheduleCheckActiveView, 60);
                        return ret;
                    };
                }

                // Hook messaging view functions for instant 0ms dock toggle between inbox and chats
                var origSetMsgView = window.setMsgView;
                if (typeof origSetMsgView === 'function') {
                    window.setMsgView = function(viewMode) {
                        var res = origSetMsgView.apply(this, arguments);
                        if (viewMode === 'thread') {
                            notifyView('chat');
                        } else if (viewMode === 'inbox') {
                            notifyView('messages');
                        }
                        return res;
                    };
                }
                var origOpenThread = window.openThread;
                if (typeof origOpenThread === 'function') {
                    window.openThread = function() {
                        notifyView('chat');
                        return origOpenThread.apply(this, arguments);
                    };
                }
                var origOpenGroupThread = window.openGroupThread;
                if (typeof origOpenGroupThread === 'function') {
                    window.openGroupThread = function() {
                        notifyView('chat');
                        return origOpenGroupThread.apply(this, arguments);
                    };
                }
                var origMsgThreadBack = window.msgThreadBack;
                if (typeof origMsgThreadBack === 'function') {
                    window.msgThreadBack = function() {
                        var res = origMsgThreadBack.apply(this, arguments);
                        notifyView('messages');
                        setTimeout(scheduleCheckActiveView, 60);
                        return res;
                    };
                }
                var origBackToMsgInbox = window.backToMsgInbox;
                if (typeof origBackToMsgInbox === 'function') {
                    window.backToMsgInbox = function() {
                        var res = origBackToMsgInbox.apply(this, arguments);
                        notifyView('messages');
                        setTimeout(scheduleCheckActiveView, 60);
                        return res;
                    };
                }

                // Initial view detection (e.g. active tab, modal or view)
                function checkCurrentActiveView() {
                    // Check if messages overlay is active: distinguish between whole messages inbox and an active chat thread!
                    var msgOverlay = document.getElementById('messagesOverlay');
                    if (msgOverlay && msgOverlay.classList.contains('active')) {
                        var inChat = (typeof window.msgView !== 'undefined' && window.msgView === 'thread') ||
                                     !msgOverlay.classList.contains('msg-inbox-open') ||
                                     msgOverlay.querySelector('.msg-input-row, #msgTextInput, .msg-page') !== null;
                        if (inChat) {
                            notifyView('chat'); // Hides the dock in chats so it doesn't overlap the message box, send button, etc.!
                            return;
                        } else {
                            notifyView('messages'); // Keeps dock buttons in the whole messages inbox!
                            return;
                        }
                    }

                    // If any overlay/modal/sheet is active (place detail card, comments, navigation, etc.),
                    // except the upload options sheet, hide the floating dock so it doesn't overlap action buttons!
                    var activeOverlay = document.querySelector(
                        '#overlay.active, #commentsOverlay.active, #postViewerOverlay.active, #userProfileOverlay.active, ' +
                        '#shareOverlay.active, #editProfileOverlay.active, #findDostOverlay.active, ' +
                        '#followListOverlay.active, #suggestedFollowsOverlay.active, #tripOverlay.active, #notifOverlay.active, ' +
                        '#mediaPreviewOverlay.active, #authModal.active, #navOverlay.active, .overlay.active:not(#uploadChooserOverlay), .navoverlay.active'
                    );
                    if (activeOverlay) {
                        var overlayId = activeOverlay.id || 'detail';
                        notifyView(overlayId.replace('Overlay', '').replace('Modal', '').toLowerCase() || 'detail');
                        return;
                    }

                    var activeEl = document.querySelector('.view.active, .screen.active, [data-view].active, [id^="view-"].active');
                    if (activeEl) {
                        var name = activeEl.getAttribute('data-view') || (activeEl.id ? activeEl.id.replace('view-', '').replace('View', '') : '');
                        if (name) notifyView(name);
                    } else if (window.currentView) {
                        notifyView(window.currentView);
                    } else {
                        // White-screen prevention safeguard: if no modal or overlay is open and no view is active, restore view-discover
                        var overlayOpen = document.querySelector('.overlay.active, .navoverlay.active, .authmodal.active');
                        if (!overlayOpen) {
                            var fallbackView = document.getElementById('view-discover') || document.getElementById('view-feed');
                            if (fallbackView) {
                                fallbackView.classList.add('active');
                                notifyView('discover');
                            }
                        }
                    }
                }

                // Debounce view checking via requestAnimationFrame to avoid DOM layout thrashing during scroll
                var checkActiveViewScheduled = false;
                function scheduleCheckActiveView() {
                    if (checkActiveViewScheduled) return;
                    checkActiveViewScheduled = true;
                    requestAnimationFrame(function() {
                        checkActiveViewScheduled = false;
                        checkCurrentActiveView();
                    });
                }

                setTimeout(checkCurrentActiveView, 100);
                setTimeout(checkCurrentActiveView, 500);
                setTimeout(checkCurrentActiveView, 1200);

                // Observe DOM mutations with debouncing for silky-smooth 60/120fps scrolling
                var viewObserver = new MutationObserver(function() {
                    scheduleCheckActiveView();
                });
                viewObserver.observe(document.body || document.documentElement, {
                    attributes: true,
                    subtree: true,
                    attributeFilter: ['class']
                });

                // Immediate capture-phase click listener on place cards and chat threads so the dock reacts instantly
                document.addEventListener('click', function(e) {
                    var card = e.target.closest('.place-card, [onclick*="openDetail"], #sheetList > div, [data-action="detail"]');
                    if (card) {
                        notifyView('detail');
                    }
                    var chatItem = e.target.closest('[onclick*="openThread"], [onclick*="openGroupThread"], .msg-inbox-item, [data-thread]');
                    if (chatItem) {
                        notifyView('chat');
                    }
                    var backFromChat = e.target.closest('.msg-back-btn, [onclick*="msgThreadBack"], [onclick*="backToMsgInbox"]');
                    if (backFromChat) {
                        setTimeout(scheduleCheckActiveView, 60);
                    }
                    var closeBtn = e.target.closest('.detail-close, [onclick*="closeDetail"], .overlay:not(#uploadChooserOverlay)');
                    if (closeBtn) {
                        setTimeout(scheduleCheckActiveView, 60);
                    }
                }, true);

                // Fast & smooth keyboard opening / closing handler for chat textbox
                document.addEventListener('focusin', function(e) {
                    if (e.target && (e.target.id === 'msgTextInput' || e.target.closest('.msg-input-row'))) {
                        document.body.classList.add('kb-open');
                        notifyView('chat');
                        var msgList = document.getElementById('msgList');
                        if (msgList) {
                            requestAnimationFrame(function() {
                                msgList.scrollTop = msgList.scrollHeight;
                            });
                        }
                    }
                }, true);

                document.addEventListener('focusout', function(e) {
                    if (e.target && (e.target.id === 'msgTextInput' || e.target.closest('.msg-input-row'))) {
                        document.body.classList.remove('kb-open');
                    }
                }, true);

                // 5. Native Smooth Sensor & GPS Provider (60fps compass + smooth GPS)
                window.getNativeNavSensors = function() {
                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.getSmoothSensorData) {
                        try {
                            return JSON.parse(window.AndroidNativeAuth.getSmoothSensorData());
                        } catch(e) {}
                    }
                    return null;
                };

                // Continuous high-frequency orientation stabilizer for map compass & turn-by-turn navigation
                var lastHeadingDispatched = -1;
                setInterval(function() {
                    if (document.hidden) return;
                    var sensors = window.getNativeNavSensors ? window.getNativeNavSensors() : null;
                    if (sensors && typeof sensors.heading === 'number') {
                        var heading = sensors.heading;

                        // Directly feed navigation heading to app's turn-by-turn engine if active
                        if (typeof window.navDeviceHeading !== 'undefined') {
                            window.navDeviceHeading = heading;
                        }

                        // Protect programmatic camera rotations from triggering breakFollow
                        if (typeof window.markNavProgrammatic === 'function') {
                            window.markNavProgrammatic();
                        }

                        // Rotate compass needle smoothly
                        var compassEl = document.getElementById('navCompassSvg') || document.querySelector('.compass-needle') || document.querySelector('[data-compass]');
                        if (compassEl) {
                            compassEl.style.transform = 'rotate(' + (-heading) + 'deg)';
                        }

                        // Intercept breakFollow if navFollowing is active and only rotate events are fired
                        if (window.navMap && !window.navMap.__breakFollowPatched && typeof window.navMap.on === 'function') {
                            window.navMap.__breakFollowPatched = true;
                            // Ensure rotatestart or programmatic setBearing never breaks auto-follow
                            try {
                                if (typeof window.navMap.off === 'function') {
                                    window.navMap.off('rotatestart');
                                }
                            } catch(e) {}
                        }

                        // Dispatch synthetic deviceorientationabsolute event for seamless web compass updates
                        if (Math.abs(heading - lastHeadingDispatched) >= 0.5) {
                            lastHeadingDispatched = heading;
                            try {
                                var evt = new Event('deviceorientationabsolute');
                                evt.alpha = (360 - heading) % 360;
                                evt.beta = 0;
                                evt.gamma = 0;
                                evt.absolute = true;
                                window.dispatchEvent(evt);
                            } catch(e) {}
                        }
                    }
                }, 50);

                // 6. Native Haptic Feedback on interactive elements
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    if (el.closest('.navitem, .iconbtn, .btn-primary, .chip, .like-btn, .heart, .plus, [role="button"], button')) {
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.performHapticFeedback) {
                            window.AndroidNativeAuth.performHapticFeedback('light');
                        }
                    }
                }, true);

                // 7. Observer to sync upload chooser sheet state with native Compose plus button and inject dynamic camera card
                var ensureChooserCameraBtn = function() {
                    var chooser = document.getElementById('uploadChooserOverlay');
                    if (!chooser) return;
                    var container = chooser.querySelector('.upload-choice') || chooser.querySelector('#uploadChooserBody');
                    if (container && !document.getElementById('uploadChoiceCameraCard')) {
                        var card = document.createElement('div');
                        card.id = 'uploadChoiceCameraCard';
                        card.className = 'upload-choice-card upload-choice-camera-card';
                        card.setAttribute('role', 'button');
                        card.style.cursor = 'pointer';
                        card.innerHTML = '<div class="upload-choice-icon" style="background: linear-gradient(135deg, #FF2A85 0%, #8A2BE2 100%); color: #fff; border-radius: 12px; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; font-size: 20px; box-shadow: 0 4px 12px rgba(255, 42, 133, 0.35);">📸</div><div class="upload-choice-text"><b>Camera & Story Maker</b><span>Capture with live filters, lenses & edit your photo</span></div>';
                        card.addEventListener('click', function(e) {
                            if (e) { e.preventDefault(); e.stopPropagation(); }
                            if (typeof closeUploadChooser === 'function') {
                                closeUploadChooser();
                            } else {
                                chooser.classList.remove('active');
                            }
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                            }
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.openNativeCamera) {
                                window.AndroidNativeAuth.openNativeCamera('post');
                            }
                        }, true);
                        container.appendChild(card);
                    }
                };

                var setupChooserObserver = function() {
                    var chooser = document.getElementById('uploadChooserOverlay');
                    if (chooser && window.MutationObserver) {
                        var notifyState = function() {
                            var isActive = chooser.classList.contains('active');
                            if (isActive) {
                                ensureChooserCameraBtn();
                            }
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                window.AndroidNativeAuth.onUploadChooserVisibilityChanged(isActive);
                            }
                        };
                        var observer = new MutationObserver(function() {
                            notifyState();
                        });
                        observer.observe(chooser, { attributes: true, attributeFilter: ['class'] });
                        notifyState();
                    }
                };
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setupChooserObserver);
                } else {
                    setupChooserObserver();
                }

                // Global helper to attach an edited photo directly to Tag a Spot or Add a Hidden Gem
                window.attachPhotoToSpot = function(targetMode, base64Data, mimeType, fileName) {
                    try {
                        var byteCharacters = atob(base64Data);
                        var byteNumbers = new Array(byteCharacters.length);
                        for (var i = 0; i < byteCharacters.length; i++) {
                            byteNumbers[i] = byteCharacters.charCodeAt(i);
                        }
                        var byteArray = new Uint8Array(byteNumbers);
                        var blob = new Blob([byteArray], { type: mimeType || 'image/jpeg' });
                        var file = new File([blob], fileName || 'spot_photo.jpg', { type: mimeType || 'image/jpeg' });

                        var viewName = (targetMode === 'gem' || targetMode === 'addgem') ? 'addgem' : 'tagspot';
                        if (typeof showView === 'function') {
                            showView(viewName);
                        }
                        setTimeout(function() {
                            if (viewName === 'tagspot') {
                                if (typeof tagMediaItems !== 'undefined') {
                                    tagMediaItems.push({ type: 'photo', file: file });
                                    if (typeof renderTagMedia === 'function') renderTagMedia();
                                }
                            } else {
                                if (typeof gemMediaItems !== 'undefined') {
                                    gemMediaItems.push({ type: 'photo', file: file });
                                    if (typeof renderGemMedia === 'function') renderGemMedia();
                                }
                            }
                            if (typeof showToast === 'function') {
                                showToast(viewName === 'tagspot' ? 'Photo attached to Tag a spot! 📸' : 'Photo attached to Hidden Gem! 💎', 3000);
                            }
                        }, 400);
                    } catch(err) {
                        console.error('attachPhotoToSpot error:', err);
                    }
                };

                // Close/hide website's general upload chooser immediately when opening music file picker
                window.openMusicFilePicker = function() {
                    try {
                        var chooser = document.getElementById('uploadChooserOverlay');
                        if (chooser) chooser.classList.remove('active');
                        if (typeof hideUploadChooserUI === 'function') hideUploadChooserUI();
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                            window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                        }
                    } catch(e) {}
                    var input = document.getElementById('musicFileInput');
                    if (input) input.click();
                };

                // Native Instagram Camera & Story Editor helper
                window.openNativeCamera = function(mode) {
                    try {
                        var chooser = document.getElementById('uploadChooserOverlay');
                        if (chooser) chooser.classList.remove('active');
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                            window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                        }
                    } catch(e) {}
                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.openNativeCamera) {
                        window.AndroidNativeAuth.openNativeCamera(mode || 'story');
                    }
                };

                // Intercept web story creation triggers
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    if (target.closest('[data-action="camera"], [data-action="story"], .story-btn, .create-story-btn, .add-story-ring')) {
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.openNativeCamera) {
                            e.preventDefault();
                            e.stopPropagation();
                            window.AndroidNativeAuth.openNativeCamera('story');
                        }
                    }
                }, true);

                document.addEventListener('click', function(e) {
                    var el = e.target;
                    if (!el || !el.closest) return;
                    var trackBtn = el.closest('#musicFileInput, button[onclick*="musicFileInput"], [data-action="add-track"]');
                    if (!trackBtn && el.tagName === 'BUTTON' && (el.textContent || '').trim().toLowerCase().indexOf('add a track') !== -1) {
                        trackBtn = el;
                    }
                    if (trackBtn) {
                        try {
                            var chooser = document.getElementById('uploadChooserOverlay');
                            if (chooser) chooser.classList.remove('active');
                            if (typeof hideUploadChooserUI === 'function') hideUploadChooserUI();
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged) {
                                window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
                            }
                        } catch(err) {}
                    }
                }, true);

                // 8. Turn-by-Turn Voice Navigation Bridge & Real-Time Observer
                var setupNavVoiceObserver = function() {
                    var navOverlayEl = document.getElementById('navOverlay');
                    var instrEl = document.getElementById('navInstrText');
                    var distEl = document.getElementById('navInstrDist');
                    var nextEl = document.getElementById('navNextInstr');
                    var destEl = document.getElementById('navDestName');

                    var lastInstruction = '';
                    var lastIsActive = false;

                    function syncTurnState() {
                        var overlay = document.getElementById('navOverlay');
                        var isActive = !!(overlay && overlay.classList.contains('active'));
                        var instr = instrEl ? (instrEl.textContent || '').trim() : '';
                        var dist = distEl ? (distEl.textContent || '').trim() : '';
                        var next = nextEl ? (nextEl.textContent || '').trim() : '';
                        var dest = destEl ? (destEl.textContent || '').trim() : '';

                        if (isActive !== lastIsActive || (isActive && instr && instr !== lastInstruction)) {
                            var transitioned = (isActive !== lastIsActive);
                            lastIsActive = isActive;
                            if (transitioned) {
                                if (isActive) {
                                    try {
                                        var dName = dest || (destEl ? destEl.textContent : '') || 'Destination';
                                        var dLat = (window.navDestPlace && window.navDestPlace.lat) || 0;
                                        var dLng = (window.navDestPlace && window.navDestPlace.lng) || 0;
                                        var tId = window.navTripPlanId || '';
                                        var steps = (window.navSteps && window.navSteps.length) ? JSON.stringify(window.navSteps) : '';
                                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.startBackgroundNavigation) {
                                            window.AndroidNativeAuth.startBackgroundNavigation(dName, dLat, dLng, tId, steps);
                                        }
                                    } catch(e) {}
                                } else {
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.stopBackgroundNavigation) {
                                        window.AndroidNativeAuth.stopBackgroundNavigation();
                                    }
                                }
                            }
                            if (isActive && instr && instr !== lastInstruction) {
                                lastInstruction = instr;
                                // Speak turn instruction out loud natively if valid guidance
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.speakTurnByTurn) {
                                    if (!instr.includes('Finding your route') && !instr.includes('Finding your location')) {
                                        window.AndroidNativeAuth.speakTurnByTurn(instr, dist);
                                    }
                                }
                            }
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.updateNavStatus) {
                                window.AndroidNativeAuth.updateNavStatus(isActive, dest, instr, dist, next);
                            }
                        }
                    }

                    if (navOverlayEl && window.MutationObserver) {
                        var navObs = new MutationObserver(function() {
                            syncTurnState();
                        });
                        navObs.observe(navOverlayEl, { attributes: true, attributeFilter: ['class'], subtree: true, characterData: true, childList: true });
                    }

                    // Poll every 350ms during navigation so GPS step advances never miss spoken alerts
                    setInterval(function() {
                        var overlay = document.getElementById('navOverlay');
                        if (overlay && overlay.classList.contains('active')) {
                            syncTurnState();
                        }
                    }, 350);

                    // Hook nav mute button to keep native voice engine synchronized
                    var muteBtn = document.getElementById('navMuteBtn');
                    if (muteBtn && !muteBtn.__voiceNativeHooked) {
                        muteBtn.__voiceNativeHooked = true;
                        muteBtn.addEventListener('click', function() {
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.toggleNavMute) {
                                window.AndroidNativeAuth.toggleNavMute();
                            }
                        }, true);
                    }

                    // Global interceptor for Location & Navigation clicks (My Location, Navigation, Directions)
                    if (!window.__locClickInterceptorHooked) {
                        window.__locClickInterceptorHooked = true;

                        document.addEventListener('click', function(e) {
                            var target = e.target;
                            if (!target || !target.closest) return;
                            var locEl = target.closest(
                                '#locateBtn, .locate-btn, .btn-locate, [data-action="locate"], ' +
                                '#locateUserBtn, #centerMapBtn, .mapboxgl-ctrl-geolocate, .leaflet-control-locate, ' +
                                '#navBtn, .nav-btn, .btn-nav, [data-action="nav"], [data-action="navigate"], ' +
                                '#startNavBtn, .start-nav, .start-nav-btn, .directions-btn, ' +
                                'button[title*="location" i], button[title*="navigate" i], button[title*="directions" i], ' +
                                'a[href*="navigate" i], [onclick*="locate" i], [onclick*="startNavigation" i]'
                            );
                            if (locEl && window.AndroidNativeAuth && window.AndroidNativeAuth.onLocationActionClicked) {
                                var actionDesc = locEl.id || locEl.getAttribute('data-action') || locEl.className || 'location_clicked';
                                window.AndroidNativeAuth.onLocationActionClicked(String(actionDesc));
                            }
                        }, true);

                        if (typeof window.startNavigation === 'function' && !window.startNavigation.__hooked) {
                            var origStartNav = window.startNavigation;
                            window.startNavigation = function() {
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.onLocationActionClicked) {
                                    window.AndroidNativeAuth.onLocationActionClicked('startNavigation');
                                }
                                return origStartNav.apply(this, arguments);
                            };
                            window.startNavigation.__hooked = true;
                        }

                        if (navigator.geolocation && !navigator.geolocation.__hooked) {
                            var origGetPos = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
                            navigator.geolocation.getCurrentPosition = function(success, error, options) {
                                if (window.AndroidNativeAuth && window.AndroidNativeAuth.onLocationActionClicked) {
                                    window.AndroidNativeAuth.onLocationActionClicked('getCurrentPosition');
                                }
                                return origGetPos(success, function(err) {
                                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.onLocationActionClicked) {
                                        window.AndroidNativeAuth.onLocationActionClicked('geolocation_error_' + (err ? err.code : 0));
                                    }
                                    if (error) error(err);
                                }, options);
                            };
                            navigator.geolocation.__hooked = true;
                        }
                    }
                };

                // 9. Real-Time Music Playback & Lockscreen Media Sync Bridge
                var setupMusicObserver = function() {
                    function getTrackInfo() {
                        var title = 'Mera Thikaana Beats';
                        var artist = 'Ranchi Beats';
                        try {
                            if (typeof currentTrack === 'function') {
                                var t = currentTrack();
                                if (t) {
                                    title = t.title || title;
                                    artist = t.artist || (t.uploader_handle ? '@' + t.uploader_handle : '') || 'Mera Thikaana';
                                }
                            }
                            if (navigator.mediaSession && navigator.mediaSession.metadata) {
                                var meta = navigator.mediaSession.metadata;
                                if (meta.title) title = meta.title;
                                if (meta.artist) artist = meta.artist;
                            }
                            var barTitle = document.getElementById('musicBarTitle');
                            var barSub = document.getElementById('musicBarSub');
                            if (barTitle && barTitle.textContent && barTitle.textContent.trim()) {
                                title = barTitle.textContent.trim();
                            }
                            if (barSub && barSub.textContent && barSub.textContent.trim()) {
                                artist = barSub.textContent.trim();
                            }
                        } catch(e) {}
                        return { title: title, artist: artist };
                    }

                    function syncMusicState(forceIsPlaying) {
                        try {
                            var a = document.getElementById('musicAudio');
                            var isPlaying = false;
                            var duration = 0;
                            var position = 0;

                            if (a) {
                                isPlaying = (typeof forceIsPlaying === 'boolean') ? forceIsPlaying : (!a.paused && !a.ended && a.readyState > 1);
                                duration = Math.round(a.duration || 0);
                                position = Math.round(a.currentTime || 0);
                            } else if (typeof musicIsPlaying === 'function') {
                                isPlaying = musicIsPlaying();
                            }

                            var info = getTrackInfo();
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.onMusicPlaybackStateChanged) {
                                window.AndroidNativeAuth.onMusicPlaybackStateChanged(isPlaying, info.title, info.artist, duration, position);
                            }
                        } catch(err) {
                            console.error('Music sync error', err);
                        }
                    }

                    function hookAudioElement() {
                        var a = document.getElementById('musicAudio');
                        if (a && !a.__nativeMediaHooked) {
                            a.__nativeMediaHooked = true;
                            a.addEventListener('play', function() { syncMusicState(true); });
                            a.addEventListener('playing', function() { syncMusicState(true); });
                            a.addEventListener('pause', function() { syncMusicState(false); });
                            a.addEventListener('ended', function() { syncMusicState(false); });
                            a.addEventListener('timeupdate', function() {
                                if (!a.__lastPosSync || Date.now() - a.__lastPosSync > 4000) {
                                    a.__lastPosSync = Date.now();
                                    syncMusicState(!a.paused);
                                }
                            });
                        }
                    }

                    // Hook navigator.mediaSession action handlers
                    if (navigator.mediaSession && !navigator.mediaSession.__nativeHooked) {
                        navigator.mediaSession.__nativeHooked = true;
                        try {
                            var origSetActionHandler = navigator.mediaSession.setActionHandler ? navigator.mediaSession.setActionHandler.bind(navigator.mediaSession) : null;
                            if (origSetActionHandler) {
                                navigator.mediaSession.setActionHandler = function(action, handler) {
                                    return origSetActionHandler(action, function(details) {
                                        if (handler) handler(details);
                                        setTimeout(function() { syncMusicState(); }, 50);
                                    });
                                };
                            }
                        } catch(e) {}
                    }

                    // Hook updateMediaSession & toggleMusicPlay
                    if (typeof window.updateMediaSession === 'function' && !window.updateMediaSession.__nativeHooked) {
                        var origUpdateMediaSession = window.updateMediaSession;
                        window.updateMediaSession = function() {
                            var r = origUpdateMediaSession.apply(this, arguments);
                            setTimeout(function() { syncMusicState(); }, 60);
                            return r;
                        };
                        window.updateMediaSession.__nativeHooked = true;
                    }

                    if (typeof window.toggleMusicPlay === 'function' && !window.toggleMusicPlay.__nativeHooked) {
                        var origToggleMusicPlay = window.toggleMusicPlay;
                        window.toggleMusicPlay = function() {
                            var r = origToggleMusicPlay.apply(this, arguments);
                            setTimeout(function() { syncMusicState(); }, 80);
                            return r;
                        };
                        window.toggleMusicPlay.__nativeHooked = true;
                    }

                    hookAudioElement();
                    setInterval(function() {
                        hookAudioElement();
                        var a = document.getElementById('musicAudio');
                        if (a && !a.paused) {
                            syncMusicState(true);
                        }
                    }, 2500);
                };

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        setupNavVoiceObserver();
                        setupMusicObserver();
                    });
                } else {
                    setupNavVoiceObserver();
                    setupMusicObserver();
                }

                // 10. Native System Notification & FCM Token Bridge
                if (typeof window.Notification === 'undefined' || !window.Notification) {
                    window.Notification = function(title, options) {
                        options = options || {};
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.showNativeNotification) {
                            window.AndroidNativeAuth.showNativeNotification(
                                title || 'Mera Thikaana',
                                options.body || '',
                                (options.data && options.data.type) || 'social',
                                options.tag || (options.data && options.data.id) || '',
                                (options.data && options.data.url) || ''
                            );
                        }
                    };
                    window.Notification.permission = (window.AndroidNativeAuth && window.AndroidNativeAuth.isNotificationPermissionGranted && window.AndroidNativeAuth.isNotificationPermissionGranted()) ? 'granted' : 'default';
                    window.Notification.requestPermission = function() {
                        if (window.AndroidNativeAuth && window.AndroidNativeAuth.requestNotificationPermission) {
                            window.AndroidNativeAuth.requestNotificationPermission();
                        }
                        return Promise.resolve('granted');
                    };
                }

                window.syncNativeFcmToken = async function(token) {
                    var t = token || window.androidFcmToken || (window.AndroidNativeAuth && window.AndroidNativeAuth.getFcmToken ? window.AndroidNativeAuth.getFcmToken() : null);
                    if (!t) return;
                    window.androidFcmToken = t;
                    try {
                        var res = await fetch('/api/save-fcm-token', {
                            method: 'POST',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ token: t, platform: 'android' })
                        });
                        console.log('[NativeBridge] Synced FCM token with backend: ' + res.status);
                    } catch(e) {
                        console.warn('[NativeBridge] FCM token sync fetch failed:', e);
                    }
                    if (window.AndroidNativeAuth && window.AndroidNativeAuth.syncFcmToken) {
                        var uid = (typeof currentUser !== 'undefined' && currentUser && currentUser.id) ? String(currentUser.id) : '';
                        window.AndroidNativeAuth.syncFcmToken(uid);
                    }
                };

                // Sync FCM token automatically once page loads if token is available
                if (window.AndroidNativeAuth && window.AndroidNativeAuth.getFcmToken) {
                    try {
                        var existingToken = window.AndroidNativeAuth.getFcmToken();
                        if (existingToken) {
                            window.androidFcmToken = existingToken;
                            setTimeout(function() { window.syncNativeFcmToken(existingToken); }, 500);
                        }
                    } catch(e) {}
                }

                // Hook badge refresh to sync native notifications
                try {
                    var origRefreshNotif = window.refreshNotifBadge;
                    if (typeof origRefreshNotif === 'function') {
                        window.refreshNotifBadge = async function() {
                            var ret = await origRefreshNotif.apply(this, arguments);
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.syncNotificationsNow) {
                                window.AndroidNativeAuth.syncNotificationsNow();
                            }
                            return ret;
                        };
                    }
                } catch(e) {}

                // 11. Native Share Sheet & WhatsApp Direct Bridge
                if (window.AndroidNativeAuth) {
                    if (window.AndroidNativeAuth.shareText) {
                        navigator.share = function(data) {
                            data = data || {};
                            window.AndroidNativeAuth.shareText(data.title || '', data.text || '', data.url || '');
                            return Promise.resolve();
                        };
                    }
                    if (window.AndroidNativeAuth.shareToWhatsApp) {
                        var origWindowOpen = window.open;
                        window.open = function(url, target, features) {
                            if (url && (url.indexOf('wa.me') !== -1 || url.indexOf('whatsapp:') !== -1 || url.indexOf('api.whatsapp.com') !== -1)) {
                                try {
                                    var parsed = new URL(url, window.location.href);
                                    var textParam = parsed.searchParams.get('text') || '';
                                    window.AndroidNativeAuth.shareToWhatsApp(textParam, '');
                                    return null;
                                } catch(e) {
                                    window.AndroidNativeAuth.shareToWhatsApp(url, '');
                                    return null;
                                }
                            }
                            return origWindowOpen ? origWindowOpen.apply(this, arguments) : null;
                        };
                    }
                }

                // 12. Native Badges & Counts Synchronization (Bell, Messages, Vibes, Find Dost)
                (function() {
                    try {
                        var styleId = 'thikana-native-badge-styles';
                        if (!document.getElementById(styleId)) {
                            var style = document.createElement('style');
                            style.id = styleId;
                            style.textContent = '.notif-badge, .msg-badge, .vibes-badge, .dost-badge {' +
                                'position: absolute !important;' +
                                'top: -3px !important;' +
                                'right: -3px !important;' +
                                'min-width: 16px !important;' +
                                'height: 16px !important;' +
                                'padding: 0 4px !important;' +
                                'border-radius: 100px !important;' +
                                'background: linear-gradient(135deg, #FF3B5C, #E11D48) !important;' +
                                'color: #ffffff !important;' +
                                'font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;' +
                                'font-size: 9px !important;' +
                                'font-weight: 700 !important;' +
                                'line-height: 16px !important;' +
                                'text-align: center !important;' +
                                'box-shadow: 0 0 0 2px #ffffff, 0 2px 5px rgba(255, 59, 92, 0.4) !important;' +
                                'display: none;' +
                                'align-items: center !important;' +
                                'justify-content: center !important;' +
                                'pointer-events: none !important;' +
                                'z-index: 25 !important;' +
                            '}' +
                            '.iconbtn { position: relative !important; transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), background-color 0.2s ease !important; }' +
                            '.iconbtn:active { transform: scale(0.88) !important; }' +
                            '.iconbtn[onclick*="openNotifications"]:hover svg, .iconbtn[onclick*="openNotifications"].has-unread svg { animation: thikanaBellRing 0.7s ease infinite alternate; }' +
                            '@keyframes thikanaBellRing { 0% { transform: rotate(0deg); } 25% { transform: rotate(14deg); } 50% { transform: rotate(-14deg); } 75% { transform: rotate(8deg); } 100% { transform: rotate(0deg); } }' +
                            '.music-btn.playing svg, .iconbtn[onclick*="openMusicPanel"].playing svg { animation: thikanaMusicPulse 1.2s ease-in-out infinite; }' +
                            '@keyframes thikanaMusicPulse { 0% { transform: scale(1) rotate(0deg); } 50% { transform: scale(1.18) rotate(8deg); } 100% { transform: scale(1) rotate(0deg); } }' +
                            '.iconbtn[onclick*="openFindDost"]:hover svg { animation: thikanaDostBounce 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) infinite alternate; }' +
                            '@keyframes thikanaDostBounce { from { transform: translateY(0); } to { transform: translateY(-3px) scale(1.12); } }' +
                            '.navitem { position: relative !important; transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1) !important; }' +
                            '.navitem:active { transform: scale(0.9) !important; }' +
                            '.navitem svg { transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1) !important; }' +
                            '.navitem.active svg { transform: scale(1.12); }';
                            document.head.appendChild(style);
                        }

                        var counts = {
                            messages: 0,
                            notifs: 0,
                            vibes: 0,
                            dost: 0
                        };

                        function pushCountsToNative() {
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.updateBadgeCounts) {
                                window.AndroidNativeAuth.updateBadgeCounts(
                                    counts.messages,
                                    counts.notifs,
                                    counts.vibes,
                                    counts.dost
                                );
                            }
                        }

                        function ensureBadgeElements() {
                            document.querySelectorAll('.iconbtn[onclick*="openNotifications"]').forEach(function(btn) {
                                btn.style.position = 'relative';
                                if (!btn.querySelector('.notif-badge')) {
                                    var span = document.createElement('span');
                                    span.className = 'notif-badge';
                                    btn.appendChild(span);
                                }
                            });
                            document.querySelectorAll('.iconbtn[onclick*="openFindDost"]').forEach(function(btn) {
                                btn.style.position = 'relative';
                                if (!btn.querySelector('.dost-badge')) {
                                    var span = document.createElement('span');
                                    span.className = 'dost-badge';
                                    btn.appendChild(span);
                                }
                            });
                            document.querySelectorAll('.navitem[data-v="feed"]').forEach(function(item) {
                                item.style.position = 'relative';
                                if (!item.querySelector('.vibes-badge')) {
                                    var span = document.createElement('span');
                                    span.className = 'vibes-badge';
                                    item.appendChild(span);
                                }
                            });
                            document.querySelectorAll('.navitem[data-v="messages"]').forEach(function(item) {
                                item.style.position = 'relative';
                                if (!item.querySelector('.msg-badge')) {
                                    var span = document.createElement('span');
                                    span.className = 'msg-badge';
                                    item.appendChild(span);
                                }
                            });
                        }

                        window.updateNotifBadgeCount = function(num) {
                            ensureBadgeElements();
                            counts.notifs = Math.max(0, parseInt(num, 10) || 0);
                            var text = counts.notifs > 99 ? '99+' : (counts.notifs > 0 ? '' + counts.notifs : '');
                            document.querySelectorAll('.notif-badge').forEach(function(el) {
                                el.textContent = text;
                                el.style.display = counts.notifs > 0 ? 'flex' : 'none';
                            });
                            pushCountsToNative();
                        };

                        window.updateMsgBadgeCount = function(num) {
                            ensureBadgeElements();
                            counts.messages = Math.max(0, parseInt(num, 10) || 0);
                            var text = counts.messages > 99 ? '99+' : (counts.messages > 0 ? '' + counts.messages : '');
                            document.querySelectorAll('.msg-badge').forEach(function(el) {
                                el.textContent = text;
                                el.style.display = counts.messages > 0 ? 'flex' : 'none';
                            });
                            pushCountsToNative();
                        };

                        window.updateVibesBadgeCount = function(num) {
                            ensureBadgeElements();
                            counts.vibes = Math.max(0, parseInt(num, 10) || 0);
                            var text = counts.vibes > 99 ? '99+' : (counts.vibes > 0 ? '' + counts.vibes : '');
                            document.querySelectorAll('.vibes-badge').forEach(function(el) {
                                el.textContent = text;
                                el.style.display = counts.vibes > 0 ? 'flex' : 'none';
                            });
                            pushCountsToNative();
                        };

                        window.updateDostBadgeCount = function(num) {
                            ensureBadgeElements();
                            counts.dost = Math.max(0, parseInt(num, 10) || 0);
                            var text = counts.dost > 99 ? '99+' : (counts.dost > 0 ? '' + counts.dost : '');
                            document.querySelectorAll('.dost-badge').forEach(function(el) {
                                el.textContent = text;
                                el.style.display = counts.dost > 0 ? 'flex' : 'none';
                            });
                            pushCountsToNative();
                        };

                        var origUpdateNotif = window.updateNotifBadge;
                        window.updateNotifBadge = function(c) {
                            if (typeof origUpdateNotif === 'function') origUpdateNotif(c);
                            window.updateNotifBadgeCount(c);
                        };

                        var origUpdateMsg = window.updateMsgBadge;
                        window.updateMsgBadge = function(c) {
                            if (typeof origUpdateMsg === 'function') origUpdateMsg(c);
                            window.updateMsgBadgeCount(c);
                        };

                        var origShowPill = window.showFeedNewPill;
                        window.showFeedNewPill = function(cnt) {
                            if (typeof origShowPill === 'function') origShowPill(cnt);
                            window.updateVibesBadgeCount(cnt);
                        };

                        var origHidePill = window.hideFeedNewPill;
                        window.hideFeedNewPill = function() {
                            if (typeof origHidePill === 'function') origHidePill();
                            window.updateVibesBadgeCount(0);
                        };

                        var origShowView = window.showView;
                        if (typeof origShowView === 'function') {
                            window.showView = function(viewName) {
                                var res = origShowView.apply(this, arguments);
                                if (viewName === 'feed') window.updateVibesBadgeCount(0);
                                else if (viewName === 'discover') window.updateDostBadgeCount(0);
                                return res;
                            };
                        }

                        var origOpenFindDost = window.openFindDost;
                        if (typeof origOpenFindDost === 'function') {
                            window.openFindDost = function() {
                                window.updateDostBadgeCount(0);
                                return origOpenFindDost.apply(this, arguments);
                            };
                        }

                        var origOpenNotifs = window.openNotifications;
                        if (typeof origOpenNotifs === 'function') {
                            window.openNotifications = function() {
                                window.updateNotifBadgeCount(0);
                                return origOpenNotifs.apply(this, arguments);
                            };
                        }

                        var origOpenMessages = window.openMessages;
                        if (typeof origOpenMessages === 'function') {
                            window.openMessages = function() {
                                window.updateMsgBadgeCount(0);
                                return origOpenMessages.apply(this, arguments);
                            };
                        }

                        function syncCurrentUserToNative() {
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.updateCurrentUser) {
                                var u = window.currentUser;
                                if (u) {
                                    window.AndroidNativeAuth.updateCurrentUser(
                                        String(u.id || ''),
                                        u.handle || '',
                                        u.avatar_url || '',
                                        u.display_name || u.handle || ''
                                    );
                                } else {
                                    window.AndroidNativeAuth.updateCurrentUser('', '', '', '');
                                }
                            }
                        }

                        function preloadMusicTracksToNative() {
                            if (window.AndroidNativeAuth && window.AndroidNativeAuth.preloadMusicTrack && Array.isArray(window.MUSIC_TRACKS)) {
                                window.MUSIC_TRACKS.forEach(function(t) {
                                    if (t && t.url) {
                                        window.AndroidNativeAuth.preloadMusicTrack(t.url);
                                    }
                                });
                            }
                        }

                        async function pollAllBadges() {
                            ensureBadgeElements();
                            syncCurrentUserToNative();
                            preloadMusicTracksToNative();
                            if (typeof window.refreshNotifBadge === 'function') {
                                try { await window.refreshNotifBadge(); } catch(e) {}
                            }
                            if (typeof window.refreshUnreadBadge === 'function') {
                                try { await window.refreshUnreadBadge(); } catch(e) {}
                            }
                            if (window.currentUser && typeof window.apiGet === 'function') {
                                try {
                                    var r = await window.apiGet('/api/users?suggested=1');
                                    if (r && Array.isArray(r.users)) {
                                        var unfollowed = r.users.filter(function(u) { return !u.is_following; });
                                        if (unfollowed.length > 0) {
                                            window.updateDostBadgeCount(unfollowed.length);
                                        }
                                    }
                                } catch(e) {}
                            }
                        }

                        setTimeout(pollAllBadges, 1200);
                        setInterval(pollAllBadges, 20000);
                    } catch(e) {}
                })();
            })();
        """.trimIndent()

        view?.evaluateJavascript(bridgeJs, null)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val description = error?.description?.toString() ?: "Network error (${error?.errorCode})"
            Log.e("ThikanaWebClient", "Main frame load error: $description for ${request.url}")
            onReceivedLoadError(description)
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val didCrash = detail?.didCrash() ?: false
        val priority = detail?.rendererPriorityAtExit() ?: -1
        Log.w("ThikanaWebClient", "Render process gone (didCrash=$didCrash, priorityAtExit=$priority)")
        
        // Critical: Must return true to tell Android the host app has handled the event,
        // preventing the OS from terminating the entire application.
        try {
            (view?.parent as? ViewGroup)?.removeView(view)
            view?.destroy()
        } catch (e: Exception) {
            Log.e("ThikanaWebClient", "Error tearing down crashed render view", e)
        }

        onReceivedLoadError(
            if (didCrash) "Web renderer encountered a crash and was safely recovered. Tap Retry to reload."
            else "Web renderer stopped. Tap Retry to reload."
        )
        onRenderProcessCrashed?.invoke()
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
        val path = uri.path ?: ""
        val urlStr = uri.toString()

        // Intercept and stream from local disk cache for music and audio playback
        if (com.example.music.ThikanaMusicCacheManager.isAudioRequest(urlStr)) {
            val audioResponse = com.example.music.ThikanaMusicCacheManager.handleAudioRequest(request, urlStr)
            if (audioResponse != null) {
                return audioResponse
            }
        }

        // Intercept and serve map tiles/styles/sprites/glyphs from local disk cache so the
        // map doesn't re-download everything from scratch on every app open. First load per
        // asset still hits the network (and caches the result); every launch after that is
        // served from disk until the 30-day cache TTL lapses.
        if (com.example.maps.ThikanaMapCacheManager.isMapTileRequest(urlStr)) {
            val mapResponse = com.example.maps.ThikanaMapCacheManager.handleMapRequest(request, urlStr)
            if (mapResponse != null) {
                return mapResponse
            }
        }

        // Serve static, rarely-changing brand assets (app icons, favicon, manifest) straight
        // from the bundled APK copy instead of the network. These barely ever change between
        // app releases, so there's no freshness reason to wait on a round-trip - and this
        // covers the very first cold launch too, before the web app's own service worker has
        // had a chance to fetch and cache them itself.
        if (isThikanaUrl(urlStr)) {
            val localAssetPath = when {
                path.startsWith("/icons/") -> "icons/" + path.substringAfterLast('/')
                path == "/favicon.ico" -> "favicon.ico"
                path == "/manifest.json" -> "manifest.json"
                else -> null
            }
            if (localAssetPath != null) {
                try {
                    val assetBytes = context.assets.open(localAssetPath).use { it.readBytes() }
                    val mimeType = when {
                        localAssetPath.endsWith(".png") -> "image/png"
                        localAssetPath.endsWith(".ico") -> "image/x-icon"
                        localAssetPath.endsWith(".json") -> "application/json"
                        else -> "application/octet-stream"
                    }
                    val encoding = if (localAssetPath.endsWith(".json")) "utf-8" else null
                    val headers = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "public, max-age=86400"
                    )
                    return WebResourceResponse(
                        mimeType,
                        encoding,
                        200,
                        "OK",
                        headers,
                        ByteArrayInputStream(assetBytes)
                    )
                } catch (e: Exception) {
                    // No bundled copy (or a read error) - fall through and let the network serve it as before
                    Log.w("ThikanaWebClient", "No bundled asset for $localAssetPath, falling back to network", e)
                }
            }
        }

        // Intercept requests for push.js to bridge Web Push into Native Android FCM
        if ((path.endsWith("/src/modules/push.js") || path.endsWith("/push.js")) && isThikanaUrl(urlStr)) {
            try {
                val assetBytes = context.assets.open("src/modules/push.js").use { it.readBytes() }
                val headers = mapOf(
                    "Content-Type" to "application/javascript; charset=utf-8",
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                )
                return WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    200,
                    "OK",
                    headers,
                    ByteArrayInputStream(assetBytes)
                )
            } catch (e: Exception) {
                Log.e("ThikanaWebClient", "Failed to load bundled asset for push.js", e)
            }
        }

        // Intercept requests for app.js to fix any ReferenceError or Temporal Dead Zone issues
        if (path.endsWith("/src/app.js") && isThikanaUrl(urlStr)) {
            try {
                // If on network, attempt to fetch and dynamically patch if needed
                val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
                }
                if (connection.responseCode in 200..299) {
                    val rawContent = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val patchedContent = patchAppJsContent(rawContent)
                    val data = patchedContent.toByteArray(Charsets.UTF_8)
                    val headers = mutableMapOf<String, String>()
                    for (i in 0 until connection.headerFields.size) {
                        val k = connection.getHeaderFieldKey(i)
                        val v = connection.getHeaderField(i)
                        if (k != null && v != null && !k.equals("Content-Length", ignoreCase = true)) {
                            headers[k] = v
                        }
                    }
                    headers["Access-Control-Allow-Origin"] = "*"
                    headers["Content-Type"] = "application/javascript; charset=utf-8"
                    return WebResourceResponse(
                        "application/javascript",
                        "utf-8",
                        200,
                        "OK",
                        headers,
                        ByteArrayInputStream(data)
                    )
                }
            } catch (e: Exception) {
                Log.w("ThikanaWebClient", "Online fetch for app.js intercept failed (${e.message}), falling back to bundled asset")
            }

            // Fallback to bundled asset
            try {
                val assetBytes = context.assets.open("src/app.js").use { it.readBytes() }
                val headers = mapOf(
                    "Content-Type" to "application/javascript; charset=utf-8",
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                )
                return WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    200,
                    "OK",
                    headers,
                    ByteArrayInputStream(assetBytes)
                )
            } catch (e: Exception) {
                Log.e("ThikanaWebClient", "Failed to load bundled asset for app.js", e)
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    companion object {
        fun patchAppJsContent(content: String): String {
            var patched = content
            val initIdx = patched.indexOf("/* ---------------- Init ---------------- */")
            val musicIdx = patched.indexOf("/* ---------------- Music ----------------")
            val exposeIdx = patched.indexOf("/* ---------------- Expose handlers referenced by inline HTML attributes ----------------")

            if (initIdx in 1 until musicIdx && musicIdx < exposeIdx) {
                val beforeInit = patched.substring(0, initIdx)
                val musicBlock = patched.substring(musicIdx, exposeIdx)
                val between = patched.substring(initIdx, musicIdx)
                val after = patched.substring(exposeIdx)
                patched = beforeInit + musicBlock + "\n" + between + after
            }

            // Ensure closeMessages() reactivates the underlying view to prevent blank white screen
            val oldCloseMessages = "function closeMessages(){\n  hideMessagesUI();\n  while(uiStack.length && (uiStack[uiStack.length-1].kind === 'messages' || uiStack[uiStack.length-1].kind === 'msgthread')) uiStack.pop();\n  history.replaceState({ depth: uiStack.length }, '', location.href);\n}"
            val newCloseMessages = "function closeMessages(){\n  hideMessagesUI();\n  while(uiStack.length && (uiStack[uiStack.length-1].kind === 'messages' || uiStack[uiStack.length-1].kind === 'msgthread')) uiStack.pop();\n  history.replaceState({ depth: uiStack.length }, '', location.href);\n  const activeName = (typeof topView === 'function' ? topView() : null) || 'discover';\n  if(typeof renderActiveView === 'function') renderActiveView(activeName);\n  try { if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.onWebViewChanged === 'function') window.AndroidNativeAuth.onWebViewChanged(activeName); } catch(e) {}\n}"
            if (patched.contains(oldCloseMessages)) {
                patched = patched.replace(oldCloseMessages, newCloseMessages)
            }

            // Safeguard renderActiveView against undefined/missing view targets
            val oldRenderActiveCheck = "const target = document.getElementById('view-'+name);\n  if(!target) return;\n  target.classList.add('active');"
            val newRenderActiveCheck = "let target = document.getElementById('view-'+name);\n  if(!target || name === 'chat' || name === 'messages' || name === 'msgthread') target = document.getElementById('view-discover') || document.getElementById('view-feed');\n  if(!target) return;\n  target.classList.add('active');"
            if (patched.contains(oldRenderActiveCheck)) {
                patched = patched.replace(oldRenderActiveCheck, newRenderActiveCheck)
            }

            return patched
        }
        /**
         * Returns true only if the URL belongs to the Thikana domain (thikana.pages.dev).
         * Safely inspects the URI host rather than doing a simple substring match
         * to prevent third-party OAuth/Google URLs (which carry origin/redirect query params)
         * from being falsely recognized as Thikana pages.
         */
        fun isThikanaUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return true
            return try {
                val uri = Uri.parse(url)
                val host = uri.host?.lowercase() ?: ""
                if (host.isEmpty()) {
                    url.startsWith("file://") || url == "about:blank"
                } else {
                    host == "thikana.pages.dev" || host.endsWith(".thikana.pages.dev")
                }
            } catch (_: Exception) {
                false
            }
        }

        private const val DOCK_OVERLAY_CSS = """
            /* Android System WebView does not reliably report env(safe-area-inset-bottom)
               the way Chrome for Android / an installed PWA does, so .navbar's own
               safe-area handling can leave it sitting partly underneath a real
               3-button/2-button system nav bar. MainActivity measures that real inset
               natively and pushes it in as --android-sys-nav-inset (0 on gesture-nav
               devices, where there's nothing to clear).

               CRITICAL FIX: We MUST NOT use `transform: translateY(...)` to lift .navbar.
               In CSS flex layout (.screen is display: flex, flex-direction: column),
               transform only visually shifts pixels without adjusting the DOM flex layout box.
               Because .mapwrap is a flex sibling above .navbar with flex: 1, translating
               .navbar upwards caused it to slide directly over the discover place cards
               (.discover-carousel-wrap) and floating map buttons, overlapping them severely.

               Instead, adjusting `margin-bottom` keeps .navbar in document flow:
               it raises .navbar above the 3-button navigation bar while naturally
               reducing .mapwrap's available height by the exact same amount. The discover carousel,
               place cards, locate button, and now-playing music bar automatically stay
               cleanly above .navbar with their intended spacing and zero overlap. */
            .navbar {
                transform: none !important;
                margin-bottom: calc(14px + var(--android-sys-nav-inset, 0px)) !important;
            }
            .music-bar {
                transform: none !important;
            }
            /* Place detail modal bottom padding for clean scrolling on all devices */
            .detail {
                padding-bottom: calc(28px + var(--android-sys-nav-inset, 0px)) !important;
            }
            .detail-actions {
                padding-bottom: calc(8px + var(--android-sys-nav-inset, 0px)) !important;
            }
            /* Ensure bottom sheets are never blocked by the system nav bar */
            #uploadChooserOverlay .detail,
            #uploadChooserBody {
                padding-bottom: calc(var(--android-sys-nav-inset, 0px) + 28px) !important;
            }
            /* Dynamic circular camera button inside upload chooser */
            #uploadChooserBody .upload-choice {
                position: relative !important;
            }
            .upload-choice-camera-btn {
                position: absolute !important;
                top: 4px !important;
                right: 4px !important;
                width: 46px !important;
                height: 46px !important;
                border-radius: 50% !important;
                background: linear-gradient(135deg, #FF2A85 0%, #8A2BE2 100%) !important;
                color: #FFFFFF !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                cursor: pointer !important;
                box-shadow: 0 4px 14px rgba(255, 42, 133, 0.45) !important;
                z-index: 100 !important;
                transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.2s !important;
                animation: cameraDynamicPulse 2.4s infinite ease-in-out !important;
            }
            .upload-choice-camera-btn:active {
                transform: scale(0.90) !important;
            }
            @keyframes cameraDynamicPulse {
                0% {
                    transform: scale(1);
                    box-shadow: 0 4px 14px rgba(255, 42, 133, 0.4), 0 0 0 0 rgba(255, 42, 133, 0.4);
                }
                50% {
                    transform: scale(1.08);
                    box-shadow: 0 6px 20px rgba(138, 43, 226, 0.55), 0 0 0 6px rgba(138, 43, 226, 0);
                }
                100% {
                    transform: scale(1);
                    box-shadow: 0 4px 14px rgba(255, 42, 133, 0.4), 0 0 0 0 rgba(255, 42, 133, 0);
                }
            }
            #shareOverlay .detail,
            #commentsOverlay .detail,
            #notifOverlay .detail {
                padding-bottom: calc(var(--android-sys-nav-inset, 0px) + 28px) !important;
            }
            /* Messages inbox list spacing above dock */
            #messagesOverlay.msg-inbox-open #messagesBody {
                padding-bottom: calc(var(--android-sys-nav-inset, 0px) + 14px) !important;
            }
            /* Inside chat threads: ensure message composer sits snugly directly above keyboard with zero extra gap */
            .msg-input-row {
                z-index: 100 !important;
                padding: 6px 12px 6px !important;
                background: #FFFFFF !important;
                position: sticky !important;
                bottom: 0 !important;
                box-sizing: border-box !important;
            }
            .msg-input-row input[type="text"] {
                font-size: 14px !important;
                padding: 8px 14px !important;
                background: var(--paper) !important;
            }
            .kb-open .msg-input-row {
                padding-top: 5px !important;
                padding-bottom: 5px !important;
            }
            .msg-page {
                height: 100% !important;
                max-height: 100% !important;
                display: flex !important;
                flex-direction: column !important;
                overflow: hidden !important;
            }
            .msg-list {
                flex: 1 !important;
                min-height: 0 !important;
                overflow-y: auto !important;
                -webkit-overflow-scrolling: touch !important;
                overscroll-behavior: contain !important;
            }
            /* Ensure in-app notification prompt (#pushSheet) and install banner are never overlapped by the floating dock */
            .installsheet,
            #pushSheet,
            #installSheet {
                bottom: calc(var(--native-dock-height, 84px) + 8px) !important;
                z-index: 950 !important;
                pointer-events: auto !important;
            }
            #routeToast {
                bottom: calc(var(--native-dock-height, 88px) + 8px) !important;
                z-index: 960 !important;
            }
            /* High-FPS rendering optimizations & responsive touch handling */
            * {
                -webkit-tap-highlight-color: transparent !important;
                touch-action: manipulation;
            }
            html, body {
                -webkit-overflow-scrolling: touch !important;
                overscroll-behavior-y: contain !important;
            }
            /* Hardware-accelerated GPU compositing for carousels, lists, and sheets */
            .discover-carousel,
            .post-gallery,
            .stories-track,
            #feedList,
            #placesList,
            .sheet-body,
            .detail {
                will-change: transform, scroll-position;
                transform: translateZ(0);
                -webkit-transform: translateZ(0);
                backface-visibility: hidden;
                -webkit-backface-visibility: hidden;
            }
        """
    }
}
