package com.glureau.wolfram30.chat

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.format.DateFormat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import com.glureau.wolfram30.chat.adapter.ChatFirebaseAdapter
import com.glureau.wolfram30.chat.adapter.ClickListenerChatFirebase
import com.glureau.wolfram30.chat.model.*
import com.glureau.wolfram30.chat.util.Util
import com.glureau.wolfram30.chat.view.FullScreenImageActivity
import com.glureau.wolfram30.chat.view.LoginActivity
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.places.ui.PlacePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import hani.momanii.supernova_emoji_library.Actions.EmojIconActions
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener, View.OnClickListener, ClickListenerChatFirebase {

    //Firebase and GoogleApiClient
    private var mFirebaseAuth: FirebaseAuth? = null
    private var mFirebaseUser: FirebaseUser? = null
    private var mGoogleApiClient: GoogleApiClient? = null
    private var mFirebaseDatabaseReference: DatabaseReference? = null
    internal var storage = FirebaseStorage.getInstance()

    //CLass Model
    private var userModel: UserModel? = null

    //Views UI
    private var rvListMessage: RecyclerView? = null
    private var mLinearLayoutManager: LinearLayoutManager? = null
    private var btSendMessage: ImageView? = null
    private var btEmoji: ImageView? = null
    private var edMessage: EmojiconEditText? = null
    private var contentRoot: View? = null
    private var emojIcon: EmojIconActions? = null

    //File
    private var filePathImageCamera: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Util.checkConnection(this)) {
            Util.initToast(this, "Você não tem conexão com internet")
            finish()
        } else {
            bindViews()
            verificaUsuarioLogado()
            mGoogleApiClient = GoogleApiClient.Builder(this)
                    .enableAutoManage(this, this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API)
                    .build()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {

        val storageRef = storage.getReferenceFromUrl(Util.URL_STORAGE_REFERENCE).child(Util.FOLDER_STORAGE_IMG)

        if (requestCode == IMAGE_GALLERY_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val selectedImageUri = data.data
                if (selectedImageUri != null) {
                    sendFileFirebase(storageRef, selectedImageUri)
                } else {
                    //URI IS NULL
                }
            }
        } else if (requestCode == IMAGE_CAMERA_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if (filePathImageCamera != null && filePathImageCamera!!.exists()) {
                    val imageCameraRef = storageRef.child(filePathImageCamera!!.name + "_camera")
                    sendFileFirebase(imageCameraRef, filePathImageCamera as File)
                } else {
                    //IS NULL
                }
            }
        } else if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val place = PlacePicker.getPlace(this, data)
                if (place != null) {
                    val latLng = place.latLng
                    val mapModel = MapModel(latLng.latitude.toString() + "", latLng.longitude.toString() + "")
                    val chatModel = ChatModel(userModel, Calendar.getInstance().time.time.toString() + "", mapModel)
                    mFirebaseDatabaseReference!!.child(CHAT_REFERENCE).push().setValue(chatModel)
                } else {
                    //PLACE IS NULL
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.sendPhoto -> verifyStoragePermissions()
            R.id.sendPhotoGallery -> photoGalleryIntent()
            R.id.sendLocation -> locationPlacesIntent()
            R.id.sign_out -> signOut()
        }//                photoCameraIntent();

        return super.onOptionsItemSelected(item)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult)
        Util.initToast(this, "Google Play Services error.")
    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.buttonMessage -> sendMessageFirebase()
        }
    }

    override fun clickImageChat(view: View, position: Int, nameUser: String, urlPhotoUser: String, urlPhotoClick: String) {
        val intent = Intent(this, FullScreenImageActivity::class.java)
        intent.putExtra("nameUser", nameUser)
        intent.putExtra("urlPhotoUser", urlPhotoUser)
        intent.putExtra("urlPhotoClick", urlPhotoClick)
        startActivity(intent)
    }

    override fun clickImageMapChat(view: View, position: Int, latitude: String, longitude: String) {
        val uri = String.format("geo:%s,%s?z=17&q=%s,%s", latitude, longitude, latitude, longitude)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        startActivity(intent)
    }


    /**
     * Envia o arvquivo para o firebase
     */
    private fun sendFileFirebase(storageReference: StorageReference?, file: Uri) {
        if (storageReference != null) {
            val name = DateFormat.format("yyyy-MM-dd_hhmmss", Date()).toString()
            val imageGalleryRef = storageReference.child(name + "_gallery")
            val uploadTask = imageGalleryRef.putFile(file)
            uploadTask.addOnFailureListener { e -> Log.e(TAG, "onFailure sendFileFirebase " + e.message) }.addOnSuccessListener { taskSnapshot ->
                Log.i(TAG, "onSuccess sendFileFirebase")
                val downloadUrl = taskSnapshot.downloadUrl
                val fileModel = FileModel("img", downloadUrl!!.toString(), name, "")
                val chatModel = ChatModel(userModel, "", Calendar.getInstance().time.time.toString() + "", fileModel)
                mFirebaseDatabaseReference!!.child(CHAT_REFERENCE).push().setValue(chatModel)
            }
        } else {
            //IS NULL
        }

    }

    /**
     * Envia o arvquivo para o firebase
     */
    private fun sendFileFirebase(storageReference: StorageReference?, file: File) {
        if (storageReference != null) {
            val photoURI = FileProvider.getUriForFile(this@MainActivity,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file)
            val uploadTask = storageReference.putFile(photoURI)
            uploadTask.addOnFailureListener { e -> Log.e(TAG, "onFailure sendFileFirebase " + e.message) }.addOnSuccessListener { taskSnapshot ->
                Log.i(TAG, "onSuccess sendFileFirebase")
                val downloadUrl = taskSnapshot.downloadUrl
                val fileModel = FileModel("img", downloadUrl!!.toString(), file.name, file.length().toString() + "")
                val chatModel = ChatModel(userModel, "", Calendar.getInstance().time.time.toString() + "", fileModel)
                mFirebaseDatabaseReference!!.child(CHAT_REFERENCE).push().setValue(chatModel)
            }
        } else {
            //IS NULL
        }

    }

    /**
     * Obter local do usuario
     */
    private fun locationPlacesIntent() {
        try {
            val builder = PlacePicker.IntentBuilder()
            startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

    }

    /**
     * Enviar foto tirada pela camera
     */
    private fun photoCameraIntent() {
        val nomeFoto = DateFormat.format("yyyy-MM-dd_hhmmss", Date()).toString()
        filePathImageCamera = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), nomeFoto + "camera.jpg")
        val it = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoURI = FileProvider.getUriForFile(this@MainActivity,
                BuildConfig.APPLICATION_ID + ".provider",
                filePathImageCamera!!)
        it.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(it, IMAGE_CAMERA_REQUEST)
    }

    /**
     * Enviar foto pela galeria
     */
    private fun photoGalleryIntent() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture_title)), IMAGE_GALLERY_REQUEST)
    }

    /**
     * Enviar msg de texto simples para chat
     */
    private fun sendMessageFirebase() {
        val model = ChatModel(userModel, edMessage!!.text.toString(), Calendar.getInstance().time.time.toString() + "", null)
        val proto = Encrypted.ChatMsg.newBuilder()
                .setId("encryption_id")
                .setTimeStamp(Calendar.getInstance().timeInMillis)
                .setMessage(edMessage!!.text.toString())
                .setUser(Encrypted.ChatMsg.UserMsg.newBuilder().setId(userModel!!.id).setName(userModel!!.name).setPhotoProfile(userModel!!.photo_profile).build())
                .build()

        val protoBA = proto.toByteArray()

        // TODO : Do encryption on ByteArray

        val encryptedModel = EncryptedModel("", protoBA)//MY_ACCOUNT, protobuf(model))

        mFirebaseDatabaseReference!!.child(ENCRYPTED_REFERENCE).push().setValue(encryptedModel)
        /**/
        edMessage!!.text = null
    }

    /**
     * Ler collections chatmodel Firebase
     */
    private fun lerMessagensFirebase() {
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().reference
        val firebaseAdapter = ChatFirebaseAdapter(mFirebaseDatabaseReference!!.child(CHAT_REFERENCE), userModel!!.name, this)
        firebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = firebaseAdapter.itemCount
                val lastVisiblePosition = mLinearLayoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisiblePosition == -1 || positionStart >= friendlyMessageCount - 1 && lastVisiblePosition == positionStart - 1) {
                    rvListMessage!!.scrollToPosition(positionStart)
                }
            }
        })
        rvListMessage!!.layoutManager = mLinearLayoutManager
        rvListMessage!!.adapter = firebaseAdapter
    }

    /**
     * Verificar se usuario está logado
     */
    private fun verificaUsuarioLogado() {
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseUser = mFirebaseAuth!!.currentUser
        if (mFirebaseUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            userModel = UserModel(mFirebaseUser!!.displayName, mFirebaseUser!!.photoUrl!!.toString(), mFirebaseUser!!.uid)
            lerMessagensFirebase()
        }
    }

    /**
     * Vincular views com Java API
     */
    private fun bindViews() {
        contentRoot = findViewById(R.id.contentRoot)
        edMessage = findViewById<View>(R.id.editTextMessage) as EmojiconEditText
        btSendMessage = findViewById<View>(R.id.buttonMessage) as ImageView
        btSendMessage!!.setOnClickListener(this)
        btEmoji = findViewById<View>(R.id.buttonEmoji) as ImageView
        emojIcon = EmojIconActions(this, contentRoot, edMessage, btEmoji)
        emojIcon!!.ShowEmojIcon()
        rvListMessage = findViewById<View>(R.id.messageRecyclerView) as RecyclerView
        mLinearLayoutManager = LinearLayoutManager(this)
        mLinearLayoutManager!!.stackFromEnd = true
    }

    /**
     * Sign Out no login
     */
    private fun signOut() {
        mFirebaseAuth!!.signOut()
        Auth.GoogleSignInApi.signOut(mGoogleApiClient)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     */
    fun verifyStoragePermissions() {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this@MainActivity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            )
        } else {
            // we already have permission, lets go ahead and call camera intent
            photoCameraIntent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {

        when (requestCode) {
            REQUEST_EXTERNAL_STORAGE ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    photoCameraIntent()
                }
        }
    }

    companion object {

        private val IMAGE_GALLERY_REQUEST = 1
        private val IMAGE_CAMERA_REQUEST = 2
        private val PLACE_PICKER_REQUEST = 3

        internal val TAG = MainActivity::class.java!!.getSimpleName()
        internal val CHAT_REFERENCE = "chatmodel"
        internal val ENCRYPTED_REFERENCE = "encryptedmodel"

        // Storage Permissions
        private val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
