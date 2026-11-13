package com.metrolist.music.arc

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Accounts that stopped answering as signed in, usually after the Google password was changed, which ends every
 * session. The app tells the user once and offers to sign in again, instead of quietly losing the library.
 */
object SignedOut {
    val youTube = MutableStateFlow(false)
    val drive = MutableStateFlow(false)
}
