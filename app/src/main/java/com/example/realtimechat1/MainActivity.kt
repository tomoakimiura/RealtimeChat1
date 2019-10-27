package com.example.realtimechat1

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.gms.appinvite.AppInviteInvitation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    var firebaseAuth: FirebaseAuth? = null
    var firebaseUser: FirebaseUser? = null
    var firebaseReference: DatabaseReference? = null
    var layoutManager: LinearLayoutManager? = null
    lateinit var firebaseAdapter: FirebaseRecyclerAdapter<MessageModel, MessageHolder>

    var userName: String = ""
    var userPhotoUrl: String = ""

    lateinit var mGoogleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        resisterNotificationChanel()

        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)

        logInCheck()

        firebaseReference = FirebaseDatabase.getInstance().reference!!
        displayChatData()


        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        buttonSend.setOnClickListener {
            postMessage()
        }
        buttonAddPhoto.setOnClickListener {
            postImage()
        }
    }

    private fun resisterNotificationChanel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val name = "Channel1"
            val descriptionText = "新規にメッセージがあった場合に通知を表示します"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID,name,importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun displayChatData() {
        layoutManager = LinearLayoutManager(this)
        layoutManager!!.stackFromEnd = true
        chatList.layoutManager = layoutManager

        val query = firebaseReference!!.child(My_CHAT_TBL).limitToLast(50)
        val options = FirebaseRecyclerOptions.Builder<MessageModel>()
            .setQuery(query, MessageModel::class.java)
            .build()

        firebaseAdapter = object : FirebaseRecyclerAdapter<MessageModel, MessageHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageHolder {
                val view = LayoutInflater.from(parent!!.context)
                    .inflate(R.layout.chat_content, parent, false)
                return MessageHolder(view)
            }

            override fun onBindViewHolder(
                holder: MessageHolder?,
                position: Int,
                model: MessageModel?
            ) {
                setUserContents(holder!!, model!!)
                setChatContents(holder, model)
            }
        }
        chatList.adapter = firebaseAdapter

        firebaseAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver(){
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val chatMessageCount = firebaseAdapter.itemCount
                val lastVisiblePosition = layoutManager!!.findLastCompletelyVisibleItemPosition()
                if (lastVisiblePosition == -1 || positionStart >= chatMessageCount -1 && lastVisiblePosition == positionStart -1){
                    chatList.scrollToPosition(positionStart)
                }
            }
        })

        firebaseReference!!.child(My_CHAT_TBL).addChildEventListener(object : ChildEventListener{
            override fun onChildMoved(p0: DataSnapshot?, p1: String?) {

            }

            override fun onChildChanged(p0: DataSnapshot?, p1: String?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onChildAdded(snapshot: DataSnapshot?, previousChildName: String?) {
                val newMessage = snapshot!!.getValue(MessageModel::class.java)
                if(newMessage!!.userName != userName)sendNotification(newMessage)
            }

            override fun onChildRemoved(p0: DataSnapshot?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onCancelled(p0: DatabaseError?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }

    private fun sendNotification(newMessage: MessageModel) {
        val notificationId = SEND_NOTIFICATION_ID
        val pendingIntent = PendingIntent.getActivity(this,
            REQUEST_PENDING_INTENT,
            Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificationBuilder = NotificationCompat.Builder(this,CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_face_black_24dp)
            .setContentTitle(newMessage.userName)
            .setContentText(newMessage.postedMessage)
            .setAutoCancel(true)
        notificationBuilder.setContentIntent(pendingIntent)
        val notification = notificationBuilder.build()
        notification.flags = Notification.DEFAULT_LIGHTS or Notification.FLAG_AUTO_CANCEL
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

    }

    private fun setChatContents(holder: MessageHolder, model: MessageModel) {
        if (model.postedMessage != "") {
            holder.apply {
                text_posted.text = model.postedMessage
                text_posted.visibility = View.VISIBLE
                image_posted.visibility = View.GONE
            }
            return
        }
        setImageContent(holder, model)
    }

    private fun setImageContent(holder: MessageHolder, model: MessageModel) {
        val imageUrl = model.postedImageUrl
        holder.apply {
            text_posted.visibility = View.INVISIBLE
            image_posted.visibility = View.VISIBLE
        }
        if (!imageUrl.startsWith("gs://")) {
            Glide.with(holder.image_posted.context)
                .load(model.postedImageUrl)
                .into(holder.image_posted)
            return
        }
        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
        storageRef.downloadUrl.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                makeToast(this, getString(R.string.connection_failed))
            }
            val downloadUrl = task.result
            Glide.with(holder.image_posted.context)
                .load(downloadUrl)
                .into(holder.image_posted)
        }
    }

    private fun setUserContents(holder: MessageHolder, model: MessageModel) {
        holder.text_user_name.text = model.userName
        if (model.userPhotoUrl != "") {
            Glide.with(this)
                .load(Uri.parse(model.userPhotoUrl))
                .into(holder.image_user)
            return
        }
        holder.image_user.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_account_circle_black_24dp
            )
        )
    }

    override fun onResume() {
        super.onResume()
        firebaseAdapter.startListening()
    }

    override fun onPause() {
        super.onPause()
        firebaseAdapter.startListening()
    }

    private fun postImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_GET_IMAGE)
    }

    private fun postMessage() {
        val model = MessageModel(userName, userPhotoUrl, inputMessage.text.toString(), "")
        firebaseReference!!.child(My_CHAT_TBL).push().setValue(model)
        inputMessage.setText("")
    }

    private fun logInCheck() {
        firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            startActivity(Intent(this@MainActivity, SignInActivity::class.java))
            finish()
            return
        }
        setUserProfiles(firebaseUser!!)
    }

    private fun setUserProfiles(firebaseUser: FirebaseUser) {
        val nav_header = nav_view.getHeaderView(0)
        val textUserName = nav_header.findViewById<TextView>(R.id.text_user_name)
        val textUserEmail = nav_header.findViewById<TextView>(R.id.text_user_id)
        textUserName.text = firebaseUser.displayName
        textUserEmail.text = firebaseUser.email

        userName = firebaseUser.displayName!!
        userPhotoUrl = firebaseUser.photoUrl.toString()
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_menu_invite -> {
                sendInvitation()

            }
            R.id.nav_menu_sign_out -> {
                signOut()

            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun signOut() {
        FirebaseAuth.getInstance().signOut()

        mGoogleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                startActivity(Intent(this@MainActivity, SignInActivity::class.java))
                finish()
                return@addOnCompleteListener
            }
        }

    }

    private fun sendInvitation() {
        val intent = AppInviteInvitation.IntentBuilder(getString(R.string.app_invite_title))
            .setMessage(getString(R.string.app_invite_message))
            .build()
        startActivityForResult(intent, REQUEST_INVITE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_INVITE -> invitationResult(requestCode, resultCode, data)
            REQUEST_GET_IMAGE -> getImageResult(resultCode, data)
        }
    }

    private fun getImageResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            makeToast(this@MainActivity, getString(R.string.get_image_failed))
            return
        }
        if (data == null) {
            return
        }
        val uriFromDevice = data.data

        val tempMessage = MessageModel(userName, userPhotoUrl, "", "")
        firebaseReference!!.child(My_CHAT_TBL).push()
            .setValue(tempMessage) { databaseError, databaseReference ->
                if (databaseError != null) {
                    makeToast(this@MainActivity, getString(R.string.db_write_error))
                    return@setValue
                }
                val key = databaseReference.key
                val storageRef =
                    FirebaseStorage.getInstance().getReference(firebaseUser!!.uid).child(
                        key!!
                    ).child(uriFromDevice.lastPathSegment)
                putImageStorage(storageRef, uriFromDevice, key)
            }

    }

    private fun putImageStorage(storageRef: StorageReference, uriFromDevice: Uri?, key: String) {
        storageRef.putFile(uriFromDevice!!).continueWithTask { task ->
            if (!task.isSuccessful) {
            }
            return@continueWithTask storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                makeToast(this@MainActivity, getString(R.string.image_upload_error))
                return@addOnCompleteListener
            }
            val chatMessage = MessageModel(userName, userPhotoUrl, "", task.result.toString())
            firebaseReference!!.child(My_CHAT_TBL).child(key).setValue(chatMessage)
        }
    }

    private fun invitationResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            makeToast(this@MainActivity, getString(R.string.invitation_sent_error))
            return
        }
    }
}