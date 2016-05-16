package com.gabyquiles.firebaselogin;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import com.firebase.client.AuthData;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener{
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int RC_SIGN_IN = 1;

    /* A dialog that is presented until the Firebase authentication finished. */
    private ProgressDialog mAuthProgressDialog;

    /* *************************************
         *              GOOGLE                 *
         ***************************************/
    /* Request code used to invoke sign in user interactions for Google+ */
    public static final int RC_GOOGLE_LOGIN = 1;

    /* Client used to interact with Google APIs. */
    private GoogleApiClient mGoogleApiClient;

    /* A flag indicating that a PendingIntent is in progress and prevents us from starting further intents. */
    private boolean mGoogleIntentInProgress;

    /* Track whether the sign-in button has been clicked so that we know to resolve all issues preventing sign-in
     * without waiting. */
    private boolean mGoogleLoginClicked;

    /* Store the connection result from onConnectionFailed callbacks so that we can resolve them when the user clicks
     * sign-in. */
    private ConnectionResult mGoogleConnectionResult;

    /* The login button for Google */
    private SignInButton mGoogleLoginButton;

    @BindView(R.id.username_editText) EditText mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        Firebase.setAndroidContext(this);

        mAuthProgressDialog = new ProgressDialog(this);
        mAuthProgressDialog.setTitle("Loading");
        mAuthProgressDialog.setMessage("Authenticating with Firebase...");
        mAuthProgressDialog.setCancelable(false);
        mAuthProgressDialog.hide();

        // Configure sign-in to request the user's ID, email address, and basic profile. ID and
// basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

// Build a GoogleApiClient with access to GoogleSignIn.API and the options above.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from
        //   GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {

                GoogleSignInAccount acct = result.getSignInAccount();

                String email = acct.getEmail();
                AsyncTask task = new AsyncTask<Object,Void,String>() {
                    @Override
                    protected String doInBackground(Object... params){
                        String token = null;
                        try{
                            Account account = new Account((String) params[0], "com.google");
                            String scopes = "oauth2:profile email";
                            token = GoogleAuthUtil.getToken(getApplicationContext(), account, scopes, null);

                        } catch(GoogleAuthException e) {
                            Log.v(TAG, "Problem getting token");
                        } catch (IOException e) {
                            Log.v(TAG, "Problem");
                        }
                        return token;
                    }

                    @Override
                    protected void onPostExecute(String token) {

                        Log.v(TAG, "This is the token: " + token);
                        final Firebase ref = new Firebase(getApplicationContext().getString(R.string.firebase_server));

                        ref.authWithOAuthToken("google", token, new Firebase.AuthResultHandler() {
                            @Override
                            public void onAuthenticated(AuthData authData) {
                                // the Google user is now authenticated with your Firebase app
                                // Authentication just completed successfully :)
                                Map<String, String> map = new HashMap<String, String>();
                                map.put("provider", authData.getProvider());
                                if(authData.getProviderData().containsKey("displayName")) {
                                    map.put("displayName", authData.getProviderData().get("displayName").toString());
                                }
                                ref.child("users").child(authData.getUid()).setValue(map);
                            }
                            @Override
                            public void onAuthenticationError(FirebaseError firebaseError) {
                                // there was an error
                            }
                        });
                    }
                };
                Object[] params = {email};
                task.execute(params);


                // Get account information
                Log.v(TAG, "Connected");
            } else {
                mUsername.setText("Fallo conneccion");
            }
        }
    }

    @OnClick(R.id.login_button)
    public void getGoogleOAuthTokenAndLogin() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
//        mAuthProgressDialog.show();
//        /* Get OAuth token in Background */
//        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
//            String errorMessage = null;
//
//            @Override
//            protected String doInBackground(Void... params) {
//                String token = null;
//
//                try {
//                    String scope = String.format("oauth2:%s", Scopes.PLUS_LOGIN);
//
//                    token = GoogleAuthUtil.getToken(MainActivity.this, Plus.API.getAccountName(mGoogleApiClient), scope);
//                } catch (IOException transientEx) {
//                    /* Network or server error */
//                    Log.e(TAG, "Error authenticating with Google: " + transientEx);
//                    errorMessage = "Network error: " + transientEx.getMessage();
//                } catch (UserRecoverableAuthException e) {
//                    Log.w(TAG, "Recoverable Google OAuth error: " + e.toString());
//                    /* We probably need to ask for permissions, so start the intent if there is none pending */
//                    if (!mGoogleIntentInProgress) {
//                        mGoogleIntentInProgress = true;
//                        Intent recover = e.getIntent();
//                        startActivityForResult(recover, RC_GOOGLE_LOGIN);
//                    }
//                } catch (GoogleAuthException authEx) {
//                    /* The call is not ever expected to succeed assuming you have already verified that
//                     * Google Play services is installed. */
//                    Log.e(TAG, "Error authenticating with Google: " + authEx.getMessage(), authEx);
//                    errorMessage = "Error authenticating with Google: " + authEx.getMessage();
//                }
//                return token;
//            }
//
//            @Override
//            protected void onPostExecute(String token) {
//                mGoogleLoginClicked = false;
//                Log.v(TAG, "Finished authenticating. Token:" + token);
////                if (token != null) {
////                    /* Successfully got OAuth token, now login with Google */
////                    mFirebaseRef.authWithOAuthToken("google", token, new AuthResultHandler("google"));
////                } else if (errorMessage != null) {
////                    mAuthProgressDialog.hide();
////                    showErrorDialog(errorMessage);
////                }
//            }
//        };
//        task.execute();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
