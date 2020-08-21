/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 10/10/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cryptron.ui.auth

import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.RouterTransaction
import com.cryptron.R
import com.cryptron.app.BreadApp
import com.cryptron.legacy.presenter.customviews.PinLayout
import com.cryptron.tools.security.FingerprintUiHelper
import com.cryptron.ui.BaseController
import com.cryptron.ui.changehandlers.DialogChangeHandler
import kotlinx.android.synthetic.main.controller_pin.*
import kotlinx.android.synthetic.main.fingerprint_dialog_container.*

class AuthenticationController(
    args: Bundle
) : BaseController(args) {

    enum class Mode {
        /** Attempt biometric auth if configured, otherwise the pin is required. */
        USER_PREFERRED,
        /** Ensures the use of a pin, fails immediately if not set. */
        PIN_REQUIRED,
        /** Ensures the use of biometric auth, fails immediately if not available. */
        BIOMETRIC_REQUIRED
    }

    interface Listener {
        /** Called when the user successfully authenticates. */
        fun onAuthenticationSuccess() = Unit

        /**
         * Called when the user exhausts all authentication attempts.
         */
        fun onAuthenticationFailed() = Unit

        /** Called when the user cancels the authentication request. */
        fun onAuthenticationCancelled() = Unit
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_MODE = "mode"
    }

    constructor(
        mode: Mode = Mode.USER_PREFERRED,
        title: String? = null,
        message: String? = null
    ) : this(
        bundleOf(
            KEY_MODE to mode.name,
            KEY_TITLE to title,
            KEY_MESSAGE to message
        )
    )

    init {
        overridePopHandler(DialogChangeHandler())
        overridePushHandler(DialogChangeHandler())
    }

    private val mode by lazy {
        val context = BreadApp.getBreadContext().applicationContext
        val fingerprintManager = context.getSystemService<FingerprintManager>()
        if (fingerprintManager == null) {
            Mode.PIN_REQUIRED
        } else {
            Mode.valueOf(arg(KEY_MODE))
        }
    }

    override val layoutId: Int =
        when (mode) {
            Mode.USER_PREFERRED -> R.layout.controller_fingerprint
            Mode.PIN_REQUIRED -> R.layout.controller_pin
            Mode.BIOMETRIC_REQUIRED -> R.layout.controller_fingerprint
        }

    private val listener get() = targetController as? Listener

    private var fingerprintUiHelper: com.cryptron.tools.security.FingerprintUiHelper? = null

    override fun onCreateView(view: View) {
        super.onCreateView(view)

        when (mode) {
            Mode.BIOMETRIC_REQUIRED, Mode.USER_PREFERRED -> {
                showFingerprint()
            }
            Mode.PIN_REQUIRED -> {
                title.text = argOptional(KEY_TITLE)
                message.text = argOptional(KEY_MESSAGE)
                brkeyboard.setDeleteImage(R.drawable.ic_delete_black)
            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        when (mode) {
            Mode.PIN_REQUIRED -> {
                pin_digits.setup(brkeyboard, object : com.cryptron.legacy.presenter.customviews.PinLayout.PinLayoutListener {
                    override fun onPinInserted(pin: String?, isPinCorrect: Boolean) {
                        if (isPinCorrect) {
                            listener?.onAuthenticationSuccess()
                            router.popCurrentController()
                        }
                    }

                    override fun onPinLocked() {
                        listener?.onAuthenticationFailed()
                        router.popCurrentController()
                    }
                })
            }
            Mode.BIOMETRIC_REQUIRED, Mode.USER_PREFERRED -> {
                val fingerprintManager = activity!!.getSystemService<FingerprintManager>()
                val fingerprintUiHelperBuilder =
                    com.cryptron.tools.security.FingerprintUiHelper.FingerprintUiHelperBuilder(fingerprintManager)
                val callback = object : com.cryptron.tools.security.FingerprintUiHelper.Callback {
                    override fun onAuthenticated() {
                        listener?.onAuthenticationSuccess()
                        router.popCurrentController()
                    }

                    override fun onError() {
                        listener?.onAuthenticationFailed()
                        router.popCurrentController()
                    }
                }

                fingerprintUiHelper = fingerprintUiHelperBuilder.build(
                    fingerprint_icon as ImageView,
                    fingerprint_status as TextView,
                    callback,
                    activity!!
                )
                fingerprintUiHelper?.startListening(null)
            }
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        when (mode) {
            Mode.PIN_REQUIRED -> {
                pin_digits.cleanUp()
            }
            Mode.BIOMETRIC_REQUIRED, Mode.USER_PREFERRED -> {
                fingerprintUiHelper?.stopListening()
            }
        }
    }

    override fun handleBack(): Boolean {
        listener?.onAuthenticationCancelled()
        return super.handleBack()
    }

    private fun showFingerprint() {
        fingerprint_title.text = argOptional(KEY_TITLE)
        cancel_button.setText(R.string.Button_cancel)
        cancel_button.setOnClickListener {
            listener?.onAuthenticationCancelled()
            router.popCurrentController()
        }
        second_dialog_button.setText(R.string.Prompts_TouchId_usePin_android)
        second_dialog_button.setOnClickListener {
            if (mode == Mode.USER_PREFERRED) {
                val pinAuthenticationController = AuthenticationController(
                    mode = Mode.PIN_REQUIRED,
                    title = arg(KEY_TITLE),
                    message = arg(KEY_MESSAGE)
                )
                pinAuthenticationController.targetController = targetController
                router.popCurrentController()
                router.pushController(RouterTransaction.with(pinAuthenticationController))
            } else {
                router.popCurrentController()
            }
        }
    }
}
