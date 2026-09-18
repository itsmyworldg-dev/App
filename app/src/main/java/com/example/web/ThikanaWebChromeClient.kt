package com.example.web

import android.os.Message
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class ThikanaWebChromeClient(
    private val onProgressChange: (Int) -> Unit,
    private val onGeolocationPrompt: (String, GeolocationPermissions.Callback) -> Unit,
    private val onFileChoose: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
    private val onPopupRequested: ((WebView, Message) -> Boolean)? = null
) : WebChromeClient() {

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (view != null && resultMsg != null && onPopupRequested != null) {
            return onPopupRequested.invoke(view, resultMsg)
        }
        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChange(newProgress)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (origin != null && callback != null) {
            onGeolocationPrompt(origin, callback)
        } else {
            callback?.invoke(origin, false, false)
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return if (filePathCallback != null && fileChooserParams != null) {
            onFileChoose(filePathCallback, fileChooserParams)
        } else {
            false
        }
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        try {
            request?.grant(request.resources)
        } catch (e: Exception) {
            Log.e("ThikanaWebChrome", "Error granting permissions", e)
        }
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let {
            val level = it.messageLevel()
            val msg = "[JS ${level}] ${it.message()} -- at ${it.sourceId()}:${it.lineNumber()}"
            if (level == ConsoleMessage.MessageLevel.ERROR) {
                Log.e("ThikanaWeb", msg)
            } else {
                Log.d("ThikanaWeb", msg)
            }
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Log.i("ThikanaWebChrome", "JS Alert: $message")
        val ctx = view?.context ?: return false
        try {
            android.app.AlertDialog.Builder(ctx)
                .setTitle("Alert")
                .setMessage(message ?: "")
                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                .setOnCancelListener { result?.cancel() }
                .show()
            return true
        } catch (e: Exception) {
            result?.confirm()
            return true
        }
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        Log.i("ThikanaWebChrome", "JS Confirm: $message")
        val ctx = view?.context ?: return false
        try {
            android.app.AlertDialog.Builder(ctx)
                .setTitle("Confirm")
                .setMessage(message ?: "")
                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                .setOnCancelListener { result?.cancel() }
                .show()
            return true
        } catch (e: Exception) {
            result?.cancel()
            return true
        }
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        Log.i("ThikanaWebChrome", "JS Prompt: $message")
        val ctx = view?.context ?: return false
        try {
            val input = android.widget.EditText(ctx).apply {
                setText(defaultValue ?: "")
            }
            android.app.AlertDialog.Builder(ctx)
                .setTitle(message ?: "")
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm(input.text.toString()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                .setOnCancelListener { result?.cancel() }
                .show()
            return true
        } catch (e: Exception) {
            result?.cancel()
            return true
        }
    }
}
