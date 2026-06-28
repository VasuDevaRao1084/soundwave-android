package com.soundwave.app.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.soundwave.app.data.UserProfile

/**
 * Google Sign-In wrapper. Requires a Web Client ID from the same Google Cloud
 * project used for the web app's Supabase Google OAuth (Credentials -> OAuth
 * client IDs -> Web application type, NOT Android type, since Supabase needs
 * the OAuth code exchanged the same way as the web flow).
 */
object GoogleAuth {
    // TODO: replace with the real Web Client ID from Google Cloud Console
    // (Supabase Dashboard -> Authentication -> Providers -> Google shows this)
    const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    fun client(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun toUserProfile(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): UserProfile {
        return UserProfile(
            id = account.id ?: account.email ?: "unknown",
            email = account.email,
            name = account.displayName,
            avatarUrl = account.photoUrl?.toString()
        )
    }
}
