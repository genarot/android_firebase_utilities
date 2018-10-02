/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN = 145;
    public static final int RC_PHOTO_PICKER = 22;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    private FirebaseDatabase        mFirebaseDatabase;
    private DatabaseReference       mMessagesDatabaseReference;
    private ChildEventListener      mChildEventListener;
    private FirebaseAuth            mFirebaseAuth;
    private FirebaseAuth.AuthStateListener  mAuthStateListener;
    private FirebaseStorage         mFirebaseStorage;
    private StorageReference        mChatPhotosStorageReference;
    private FirebaseRemoteConfig    mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Initialize Firebase Components
        mFirebaseDatabase       = FirebaseDatabase.getInstance();
        mFirebaseAuth           = FirebaseAuth.getInstance();
        mFirebaseStorage        = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig   = FirebaseRemoteConfig.getInstance();

        mMessagesDatabaseReference   =   mFirebaseDatabase.getReference().child( "chat" ).child( "messages" );
        mChatPhotosStorageReference  =  mFirebaseStorage.getReference().child( "chat_photos" );

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent   = new Intent( Intent.ACTION_GET_CONTENT );
                intent.setType( "image/jpeg" );
                intent.putExtra( Intent.EXTRA_LOCAL_ONLY, true );
                startActivityForResult( Intent.createChooser( intent, "Complete action using" ), RC_PHOTO_PICKER );
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                sendMessage( mMessageEditText );
            }
        });

        mAuthStateListener  = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser    user = firebaseAuth.getCurrentUser();
                if ( user != null ) {
                    //user is signed in
                    onSignedInInitialize( user.getDisplayName() );
                } else  {
                    //user is signed out
                    onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setIsSmartLockEnabled( false )
                                .setAvailableProviders( Arrays.asList(
                                        new AuthUI.IdpConfig.GoogleBuilder().build(),
                                        new AuthUI.IdpConfig.EmailBuilder().build()
                                ))
                                .build(),
                                RC_SIGN_IN
                    );
                }
            }
        };
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled( true )
                .build();
        mFirebaseRemoteConfig.setConfigSettings( configSettings );

        Map<String, Object> defaultConfigMap    = new HashMap<>(  );
        defaultConfigMap.put( FRIENDLY_MSG_LENGTH_KEY,  DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults( defaultConfigMap );
        fetchConfig();
    }

    private void sendMessage( EditText et ) {
        String texto = et.getText().toString();
        if ( !TextUtils.isEmpty( texto ) ) {
            FriendlyMessage friendlyMessage = new FriendlyMessage( texto.trim(), mUsername, null );
            mMessagesDatabaseReference.push().setValue( friendlyMessage );
            // Clear input box
            mMessageEditText.setText( "" );
        } else {
            Toast.makeText(getApplicationContext(), "Escribe algo", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut( MainActivity.this );
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult( requestCode, resultCode, data );
        if ( requestCode == RC_SIGN_IN ) {
            if ( resultCode == RESULT_OK ) {
                Toast.makeText( this, "Signed in!", Toast.LENGTH_SHORT ).show();
            } else  if ( resultCode == RESULT_CANCELED ){
                Toast.makeText( this, "Sign in canceled", Toast.LENGTH_SHORT ).show();
                finish();
            }
        } else if ( requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
           Uri selectImageUri = data.getData();
           //Obtenemos la referencia del archivo a almacenar esta es chat_photos/<FILENAME>
           final StorageReference photoRef = mChatPhotosStorageReference.child( selectImageUri.getLastPathSegment() );

           // Subimos la imagen al almacenamiento de Firebase
            UploadTask uploadTask = photoRef.putFile( selectImageUri );
            uploadTask.continueWithTask( new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    //Si la imagen no se subio con exito lanzamos el error
                    if ( !task.isSuccessful() ) {
                        throw task.getException();
                    }
                    // Continue with the task get the download URL
                    //Si se subio, procedemos a obtener el url de descarga
                    return photoRef.getDownloadUrl();
                }
            } ).addOnCompleteListener( new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if ( task.isSuccessful() ) {
                        //Si obtuvimos el url de descarga
                        Uri  downloadUri    = task.getResult();
                        FriendlyMessage friendlyMessage = new FriendlyMessage( null, mUsername, downloadUri.toString());
                        mMessagesDatabaseReference.push().setValue( friendlyMessage );
//                        mMessageAdapter.add( friendlyMessage );
                    } else {
                        //
                        Toast.makeText( MainActivity.this, "Ocurrio un error al obtener el url de la imagen que acabas de subir!", Toast.LENGTH_SHORT ).show();
                        Log.d("MYTAG", task.getException().getMessage());
                    }
                }
            } );

            /**
             * Deprecado
             */
//           UploadTask uploadTask = photoRef.putFile( selectImageUri )
//                   .addOnSuccessListener( MainActivity.this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                       @Override
//                       public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                           Uri downloadUri  = taskSnapshot.getDownloadUrl();
//                       }
//                   } );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener( mAuthStateListener );
    }

    @Override
    protected void onPause() {
        super.onPause();
        if ( mAuthStateListener != null ) {
            mFirebaseAuth.removeAuthStateListener( mAuthStateListener );
        }
        detachDatabase();
        mMessageAdapter.clear();
    }
    private void attachDatabaseReadListener(){
        if ( mChildEventListener == null ) {
            //El manejo de los listener de los mensajes que existen en la aplicacion
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    //Cuando agregamos un nuevo mensaje
                    FriendlyMessage newFriendlyMessage = dataSnapshot.getValue( FriendlyMessage.class );
                    mMessageAdapter.add( newFriendlyMessage );
                    mMessageListView.smoothScrollToPosition( mMessageAdapter.getCount() - 1  );
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    //Cuando cambia el contenido de un mensaje
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                    //Cuando eliminamos un mensaje ya existente
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    //Cuando un mensaje cambia de posicion en la lista
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            mMessagesDatabaseReference.addChildEventListener( mChildEventListener );
        }
    }
    private void detachDatabase(){
        if ( mChildEventListener != null ) {
            mMessagesDatabaseReference.removeEventListener( mChildEventListener );
            mChildEventListener = null;
        }
    }
    private void onSignedInInitialize( String mUsername ) {
        this.mUsername = mUsername;
        //Aqui ya ha iniciado sesion
        attachDatabaseReadListener();
    }
    private void onSignedOutCleanup(){
        mMessageAdapter.clear();
        mUsername   = ANONYMOUS;
        detachDatabase();
    }
    public void fetchConfig() {
        long cacheExpiration    = 3600;

        if ( mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled() ) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener( new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        mFirebaseRemoteConfig.activateFetched();
                        applyRetrivedLengthLimit();
                    }
                } )
                .addOnFailureListener( new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        applyRetrivedLengthLimit();
                    }
                } );
    }
    private void applyRetrivedLengthLimit(){
        Long friendly_msg_length = mFirebaseRemoteConfig.getLong( FRIENDLY_MSG_LENGTH_KEY );
        if ( friendly_msg_length != null && friendly_msg_length > 0)
            mMessageEditText.setFilters( new InputFilter[]{new InputFilter.LengthFilter( friendly_msg_length.intValue() )} );
    }
}
