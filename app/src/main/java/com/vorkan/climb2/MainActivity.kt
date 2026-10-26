package com.vorkan.climb2

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.widget.EditText
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Region
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.PopupMenu
import android.app.Dialog
import android.widget.FrameLayout
import android.view.WindowManager
import android.text.TextWatcher
import android.text.Editable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val googleLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        } catch (error: Exception) {
            Toast.makeText(this, "Acceso con Google cancelado o fallido: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            null
        }
        if (account != null) {
            val token = account.idToken
            if (token.isNullOrBlank()) {
                Toast.makeText(this, "Google no ha devuelto una credencial válida.", Toast.LENGTH_LONG).show()
            } else {
                FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            showGymsHome()
                        } else {
                            Toast.makeText(this, "No se pudo iniciar sesión: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    private fun signInWithGoogle() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            Toast.makeText(this, "Firebase no está configurado en esta instalación.", Toast.LENGTH_LONG).show()
            return
        }
        val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resourceId == 0) {
            AlertDialog.Builder(this)
                .setTitle("Configuración de Google pendiente")
                .setMessage("La configuración OAuth de Google aún no está incluida en esta compilación. Puedes continuar en modo local de pruebas.")
                .setPositiveButton("Entendido", null).show()
            return
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(resourceId)).requestEmail().build()
        googleLogin.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    private lateinit var editor: HoldView
    private lateinit var status: TextView
    private var currentId: String? = null
    private var currentPhotoFile: String? = null
    private var currentName: String = ""
    private var currentGrade: String = ""
    private var activeGymId: String? = null
    private var activeCloudGymId: String? = null
    private var activeCloudGymOwner = false
    private var cloudOwnerId: String? = null
    private lateinit var editorScreen: View
    private val gymsFile by lazy { File(filesDir, "gyms-local.json") }
    private val storage: File
        get() = File(filesDir, "gyms/${activeGymId ?: "no-gym"}/problems").apply { mkdirs() }

    private fun localGyms(): JSONArray = runCatching {
        JSONArray(gymsFile.readText())
    }.getOrElse { JSONArray() }

    private fun showWelcome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(backgroundColor)
            setPadding(dp(28), dp(32), dp(28), dp(32))
        }
        root.addView(VorkanEmblem(this), LinearLayout.LayoutParams(dp(175), dp(175)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(24)
        })
        root.addView(TextView(this).apply {
            text = "VORKAN CLIMB 2.0"
            textSize = 29f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "TU PRÓXIMO MOVIMIENTO"
            textSize = 12f
            letterSpacing = 0.13f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            setPadding(0, dp(10), 0, dp(40))
        })
        root.addView(action("G  ENTRAR CON GOOGLE", accentColor, backgroundColor) {
            signInWithGoogle()
        }, LinearLayout.LayoutParams(-1, dp(62)))
        val configured = FirebaseApp.getApps(this).isNotEmpty() &&
            resources.getIdentifier("default_web_client_id", "string", packageName) != 0
        if (!configured) {
            root.addView(TextView(this).apply {
                text = "Estamos terminando la conexión con Google. Mientras tanto puedes seguir probando tus bloques en este móvil."
                setTextColor(mutedColor)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(24), dp(6), dp(12))
            })
            root.addView(action("CONTINUAR EN MODO LOCAL (PRUEBAS)") { showGymsHome() },
                LinearLayout.LayoutParams(-1, dp(54)))
        }
        setContentView(root)
    }

    private fun showGymsHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(24), dp(18), dp(12))
        }
        root.addView(TextView(this).apply {
            text = "VORKAN CLIMB 2.0"
            setTextColor(accentColor)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "MIS ROCÓDROMOS"
            setTextColor(Color.WHITE)
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(18))
        })
        val firebaseReady = FirebaseApp.getApps(this).isNotEmpty()
        val user = if (firebaseReady) FirebaseAuth.getInstance().currentUser else null
        if (user != null) {
            root.addView(TextView(this).apply {
                text = "Cuenta: ${user.displayName ?: user.email ?: "Google"}"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, dp(8))
            })
            root.addView(action("CERRAR SESIÓN") {
                FirebaseAuth.getInstance().signOut()
                GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                showWelcome()
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(16) })
        } else {
            root.addView(TextView(this).apply {
                text = "Modo local de pruebas · Sin sincronización"
                textSize = 12f
                setTextColor(mutedColor)
                setPadding(0, 0, 0, dp(14))
            })
            root.addView(action("←  VOLVER A BIENVENIDA") { showWelcome() },
                LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(12) })
        }
        val gyms = localGyms()
        for (i in 0 until gyms.length()) {
            val gym = gyms.getJSONObject(i)
            val id = gym.optString("id")
            val name = gym.optString("name")
            root.addView(action("⌂  $name  · LOCAL →") {
                activeGymId = id
                activeCloudGymId = null
                setContentView(editorScreen)
                status.text = "Rocódromo local: $name · Foto → Presas → Guardar"
            }, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(12) })
        }
        if (user != null) {
            root.addView(action("☁  MIS ROCÓDROMOS EN LA NUBE") {
                showCloudGyms()
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
            root.addView(action("＋  CREAR ROCÓDROMO EN LA NUBE", accentColor, backgroundColor) {
                showCreateCloudGym()
            }, LinearLayout.LayoutParams(-1, dp(60)))
            root.addView(action("⌕  BUSCAR ROCÓDROMOS") {
                showCloudDirectory()
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(12) })
        } else {
            root.addView(action("＋  CREAR ROCÓDROMO LOCAL (PRUEBAS)", accentColor, backgroundColor) {
                val input = EditText(this).apply { hint = "Nombre del rocódromo"; setSingleLine(true) }
                AlertDialog.Builder(this).setTitle("Nuevo rocódromo local")
                    .setView(input).setNegativeButton("Cancelar", null)
                    .setPositiveButton("Crear") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) {
                            val current = localGyms()
                            val id = UUID.randomUUID().toString()
                            current.put(JSONObject().put("id", id).put("name", name))
                            gymsFile.writeText(current.toString())
                            activeGymId = id
                            activeCloudGymId = null
                            setContentView(editorScreen)
                            status.text = "Rocódromo: $name · Crea tu primer bloque."
                        }
                    }.show()
            }, LinearLayout.LayoutParams(-1, dp(60)))
        }
        setContentView(root)
    }

    private fun cloudPage(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(24), dp(18), dp(12))
            addView(action("←  MIS ROCÓDROMOS") { showGymsHome() },
                LinearLayout.LayoutParams(-1, dp(50)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, dp(22), 0, dp(18))
            })
        }
    }

    private fun showCreateCloudGym() {
        val user = FirebaseAuth.getInstance().currentUser ?: return showWelcome()
        val nameInput = EditText(this).apply { hint = "Nombre del rocódromo"; setSingleLine(true) }
        val cityInput = EditText(this).apply { hint = "Localidad (opcional)"; setSingleLine(true) }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(nameInput)
            addView(cityInput)
        }
        AlertDialog.Builder(this).setTitle("Crear rocódromo compartido")
            .setView(fields).setNegativeButton("Cancelar", null)
            .setPositiveButton("Crear") { _, _ ->
                val name = nameInput.text.toString().trim()
                val city = cityInput.text.toString().trim()
                if (name.length !in 2..100 || city.length > 100) {
                    Toast.makeText(this, "Nombre de 2 a 100 caracteres", Toast.LENGTH_LONG).show()
                } else {
                    val doc = FirebaseFirestore.getInstance().collection("gyms").document()
                    doc.set(mapOf("name" to name, "city" to city, "ownerId" to user.uid,
                        "createdAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Rocódromo creado en la nube", Toast.LENGTH_SHORT).show()
                            showCloudGyms()
                        }.addOnFailureListener {
                            Toast.makeText(this, "Error al crear: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            }.show()
    }

    private fun showCloudGyms() {
        val user = FirebaseAuth.getInstance().currentUser ?: return showWelcome()
        val page = cloudPage("MIS ROCÓDROMOS EN LA NUBE")
        val progress = TextView(this).apply {
            text = "Cargando rocódromos…"
            setTextColor(mutedColor)
        }
        page.addView(progress)
        setContentView(page)
        FirebaseFirestore.getInstance().collection("gyms")
            .whereEqualTo("ownerId", user.uid).limit(50).get()
            .addOnSuccessListener { result ->
                progress.text = if (result.isEmpty) "Todavía no has creado rocódromos en la nube." else ""
                for (gym in result.documents) {
                    val name = gym.getString("name") ?: "Sin nombre"
                    page.addView(action("⌂  $name  · ADMINISTRADOR →") {
                        showCloudGymDetails(gym.id, name, true)
                    }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(10) })
                }
            }.addOnFailureListener {
                progress.text = "No se pudo consultar Firebase: ${it.localizedMessage}"
            }
    }

    private fun showCloudDirectory() {
        val page = cloudPage("BUSCAR ROCÓDROMOS")
        val query = EditText(this).apply {
            hint = "Escribe el nombre o la localidad"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(mutedColor)
        }
        page.addView(query, LinearLayout.LayoutParams(-1, dp(58)))
        val progress = TextView(this).apply {
            text = "Consultando Firebase…"
            setTextColor(mutedColor)
            setPadding(0, dp(12), 0, dp(12))
        }
        page.addView(progress)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)
        setContentView(page)
        fun load() {
            progress.text = "Consultando Firebase…"
            results.removeAllViews()
            FirebaseFirestore.getInstance().collection("gyms").limit(100)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    val docs = snapshot.documents
                    fun render(filter: String) {
                        results.removeAllViews()
                        val matched = docs.filter { gym ->
                            val name = gym.getString("name").orEmpty()
                            val city = gym.getString("city").orEmpty()
                            name.contains(filter, ignoreCase = true) ||
                                city.contains(filter, ignoreCase = true)
                        }
                        progress.text = if (matched.isEmpty()) {
                            if (docs.isEmpty()) "Firebase no ha devuelto rocódromos. Comprueba que PRUEBA VORKAN siga en el proyecto." 
                            else "No hay coincidencias. Prueba otro nombre."
                        } else "${matched.size} rocódromo(s) encontrado(s)"
                        for (gym in matched) {
                            val name = gym.getString("name") ?: "Sin nombre"
                            val city = gym.getString("city").orEmpty()
                            val isOwner = gym.getString("ownerId") ==
                                FirebaseAuth.getInstance().currentUser?.uid
                            results.addView(action("⌕  $name ${if (city.isNotBlank()) "· $city" else ""} →") {
                                showCloudGymDetails(gym.id, name, isOwner)
                            }, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(10) })
                        }
                    }
                    render(query.text.toString().trim())
                    query.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            render(s?.toString()?.trim().orEmpty())
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })
                }.addOnFailureListener { error ->
                    val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
                    progress.text = when (code) {
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                            "Firebase ha rechazado el acceso. Hay que revisar las reglas de Firestore."
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                            "No se puede contactar con el servidor de Firestore. Comprueba la conexión e inténtalo de nuevo."
                        else -> "Error Firebase (${code ?: "desconocido"}): ${error.localizedMessage}"
                    }
                }
        }
        page.addView(action("↻  ACTUALIZAR LISTADO") { showCloudDirectory() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        load()
    }

    private fun enterCloudGym(gymId: String, name: String) {
        activeGymId = gymId
        activeCloudGymId = gymId
        activeCloudGymOwner = FirebaseAuth.getInstance().currentUser?.uid != null &&
            FirebaseAuth.getInstance().currentUser?.uid == cloudOwnerId
        setContentView(editorScreen)
        status.text = "Rocódromo: $name · Foto → Presas → Guardar"
    }

    private fun showCloudGymDetails(gymId: String, name: String, isOwner: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return showWelcome()
        val page = cloudPage(name)
        val statusText = TextView(this).apply {
            text = if (isOwner) "Eres administrador de este rocódromo." else "Comprobando acceso…"
            textSize = 15f
            setTextColor(mutedColor)
            setPadding(0, dp(8), 0, dp(20))
        }
        page.addView(statusText)
        setContentView(page)
        if (isOwner) {
            page.addView(action("ENTRAR AL ROCÓDROMO", accentColor, backgroundColor) {
                cloudOwnerId = user.uid
                enterCloudGym(gymId, name)
            }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) })
            page.addView(action("SOLICITUDES PENDIENTES") { showGymRequests(gymId, name) },
                LinearLayout.LayoutParams(-1, dp(58)))
            return
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("gyms").document(gymId).collection("members").document(user.uid).get()
            .addOnSuccessListener { member ->
                if (member.exists()) {
                    statusText.text = "Ya eres miembro de este rocódromo."
                    page.addView(action("ENTRAR AL ROCÓDROMO", accentColor, backgroundColor) {
                        cloudOwnerId = null
                        enterCloudGym(gymId, name)
                    }, LinearLayout.LayoutParams(-1, dp(58)))
                } else {
                    db.collection("gyms").document(gymId).collection("requests").document(user.uid).get()
                        .addOnSuccessListener { request ->
                            if (request.exists() && request.getString("status") == "pending") {
                                statusText.text = "Solicitud enviada. Esperando aprobación."
                            } else {
                                statusText.text = "Para entrar necesitas autorización del administrador."
                                page.addView(action("SOLICITAR ACCESO", accentColor, backgroundColor) {
                                    db.collection("gyms").document(gymId).collection("requests")
                                        .document(user.uid).set(mapOf("userId" to user.uid,
                                            "status" to "pending", "createdAt" to FieldValue.serverTimestamp()))
                                        .addOnSuccessListener { showCloudGymDetails(gymId, name, false) }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "No se pudo solicitar: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                }, LinearLayout.LayoutParams(-1, dp(58)))
                            }
                        }.addOnFailureListener { statusText.text = "Error: ${it.localizedMessage}" }
                }
            }.addOnFailureListener { statusText.text = "Error: ${it.localizedMessage}" }
    }

    private fun showGymRequests(gymId: String, name: String) {
        val page = cloudPage("SOLICITUDES · $name")
        val progress = TextView(this).apply {
            text = "Cargando solicitudes…"
            setTextColor(mutedColor)
        }
        page.addView(progress)
        setContentView(page)
        val db = FirebaseFirestore.getInstance()
        val gym = db.collection("gyms").document(gymId)
        gym.collection("requests").whereEqualTo("status", "pending").limit(100).get()
            .addOnSuccessListener { result ->
                progress.text = if (result.isEmpty) "No hay solicitudes pendientes." else ""
                for (request in result.documents) {
                    val uid = request.id
                    page.addView(action("ACEPTAR · ${uid.take(12)}…", accentColor, backgroundColor) {
                        val batch = db.batch()
                        batch.set(gym.collection("members").document(uid),
                            mapOf("userId" to uid, "role" to "member",
                                "joinedAt" to FieldValue.serverTimestamp()))
                        batch.update(request.reference, "status", "approved")
                        batch.commit()
                            .addOnSuccessListener { showGymRequests(gymId, name) }
                            .addOnFailureListener {
                                Toast.makeText(this, "No se pudo aprobar: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
                    page.addView(action("RECHAZAR · ${uid.take(12)}…") {
                        request.reference.update("status", "rejected")
                            .addOnSuccessListener { showGymRequests(gymId, name) }
                            .addOnFailureListener {
                                Toast.makeText(this, "No se pudo rechazar: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(14) })
                }
            }.addOnFailureListener { progress.text = "Error: ${it.localizedMessage}" }
    }

    private var pendingCameraPhoto: File? = null
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraPhoto
        pendingCameraPhoto = null
        if (success && file != null && file.isFile && file.length() > 0L) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                beginNewPhoto(bitmap)
            } else {
                status.text = "No se pudo leer la fotografía de la cámara."
            }
        }
        file?.delete()
    }

    private fun beginNewPhoto(bitmap: Bitmap) {
        editor.setPhoto(bitmap)
        editor.readOnly = false
        currentId = null
        currentPhotoFile = null
        currentName = ""
        currentGrade = ""
        status.text = "Preparando detección automática de presas…"
        detectHoldsAutomatically()
    }

    private fun detectHoldsAutomatically() {
        // Prepare the bundled detector off the UI thread; users never need
        // to import an ONNX file or manually trigger detection.
        val currentPhoto = editor.photoIdentity()
        Thread {
            val model = File(filesDir, "hold-detector.onnx")
            val result = runCatching {
                if (!model.isFile || model.length() < 1_000_000L) {
                    val temporary = File(filesDir, "hold-detector.onnx.tmp")
                    try {
                        assets.open("hold-detector.onnx").use { input ->
                            temporary.outputStream().use { output -> input.copyTo(output) }
                        }
                        check(temporary.length() > 1_000_000L)
                        check(temporary.renameTo(model))
                    } finally {
                        temporary.delete()
                    }
                }
                model
            }
            runOnUiThread {
                if (editor.photoIdentity() !== currentPhoto || editor.readOnly) return@runOnUiThread
                result.onSuccess { ready ->
                    status.text = "Detectando presas automáticamente…"
                    editor.detectOffline(ready) { message -> status.text = message }
                }.onFailure {
                    status.text = "No se pudo iniciar la detección: ${it.message}"
                }
            }
        }.start()
    }

    private fun choosePhotoSource() {
        // Use the same dark VORKAN styling as the rest of the app.
        // The previous Android system alert appeared as a large white panel.
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(backgroundColor, 20)
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }
        panel.addView(TextView(this).apply {
            text = "NUEVA FOTO"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
            setPadding(dp(6), dp(4), dp(6), dp(14))
        })
        fun option(label: String, onSelect: () -> Unit) {
            panel.addView(action(label) {
                dialog.dismiss()
                onSelect()
            }, LinearLayout.LayoutParams(-1, dp(56)).apply {
                bottomMargin = dp(10)
            })
        }
        option("▧  ELEGIR DE LA GALERÍA") { picker.launch("image/*") }
        option("◎  HACER FOTO CON LA CÁMARA") {
            runCatching {
                val file = File.createTempFile("vorkan-camera-", ".jpg", cacheDir)
                pendingCameraPhoto = file
                val uri = FileProvider.getUriForFile(this,
                    "${packageName}.fileprovider", file)
                camera.launch(uri)
            }.onFailure {
                pendingCameraPhoto?.delete()
                pendingCameraPhoto = null
                status.text = "No se pudo abrir la cámara: ${it.message}"
            }
        }
        option("CANCELAR") { }
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(40), -2)
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        beginNewPhoto(bitmap)
                    } else status.text = "No se pudo decodificar la foto."
                }
            } catch (e: Exception) {
                status.text = "Error al abrir la foto: ${e.message}"
            }
        }
    }

    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            val all = storage.listFiles()?.filter { it.isFile && it.extension == "json" } ?: emptyList()
            val valid = all.mapNotNull { metadata ->
                runCatching {
                    val data = JSONObject(metadata.readText())
                    val photoName = data.getString("photo")
                    check(Regex("^[a-zA-Z0-9_-]{1,80}\\.jpg$").matches(photoName))
                    val photo = File(storage, photoName)
                    check(photo.isFile && photo.length() > 0)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(photo.absolutePath, bounds)
                    check(bounds.outWidth > 0 && bounds.outHeight > 0)
                    metadata to photo
                }.getOrNull()
            }
            check(valid.isNotEmpty()) { "No hay bloques completos para exportar" }
            val files = valid.flatMap { listOf(it.first, it.second) }.distinctBy { it.name }
            contentResolver.openOutputStream(uri)?.use { output ->
                ZipOutputStream(output).use { zip ->
                    files.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("No se pudo crear la copia")
            val omitted = all.size - valid.size
            status.text = "Copia exportada: ${valid.size} bloques." +
                if (omitted > 0) " Atención: $omitted bloques sin foto se han omitido." else ""
        }.onFailure { status.text = "Error al exportar: ${it.message}" }
    }

    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            AlertDialog.Builder(this)
                .setTitle("Restaurar copia")
                .setMessage("Se añadirán los bloques de la copia. Los bloques actuales no se sobrescribirán.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar") { _, _ ->
                    runCatching { restoreArchive(uri) }
                        .onSuccess { (added, skipped) -> status.text = "Restaurados: $added bloques nuevos." +
                            if (skipped > 0) " $skipped omitidos por falta de fotografía o datos." else "" }
                        .onFailure { status.text = "Error al restaurar: ${it.message}" }
                }.show()
        }
    }

    private fun restoreArchive(uri: Uri): Pair<Int, Int> {
        val staging = File(cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var totalBytes = 0L
            var count = 0
            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        check(!entry.isDirectory && Regex("^[a-zA-Z0-9_-]{1,80}\\.(json|jpg)$").matches(name)) {
                            "Archivo de copia no válido"
                        }
                        check(++count <= 2000) { "Demasiados archivos" }
                        val target = File(staging, name)
                        check(!target.exists()) { "Archivo duplicado en la copia" }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                totalBytes += n
                                check(totalBytes <= 400L * 1024 * 1024) { "Copia demasiado grande" }
                                output.write(buffer, 0, n)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("No se pudo leer la copia")
            var imported = 0
            var skipped = 0
            staging.listFiles()?.filter { it.extension == "json" }?.forEach { jsonFile ->
                val result = runCatching {
                    val data = JSONObject(jsonFile.readText())
                    val id = data.getString("id")
                    val photo = data.getString("photo")
                    check(Regex("^[a-zA-Z0-9_-]{1,80}$").matches(id))
                    check(Regex("^[a-zA-Z0-9_-]{1,80}\\.jpg$").matches(photo))
                    val jpgFile = File(staging, photo)
                    val existingPhoto = File(storage, photo)
                    val source = if (jpgFile.isFile && jpgFile.length() > 0) jpgFile else existingPhoto
                    check(source.isFile && source.length() > 0)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.absolutePath, options)
                    check(options.outWidth > 0 && options.outHeight > 0)
                    val metadata = File(storage, "$id.json")
                    if (!metadata.exists()) {
                        if (!existingPhoto.exists()) source.copyTo(existingPhoto)
                        jsonFile.copyTo(metadata)
                        imported++
                    }
                }
                if (result.isFailure) skipped++
            }
            return imported to skipped
        } finally {
            staging.deleteRecursively()
        }
    }

    private val backgroundColor = Color.rgb(16, 20, 25)
    private val panelColor = Color.rgb(35, 43, 51)
    private val mutedColor = Color.rgb(173, 181, 187)
    private val accentColor = Color.rgb(247, 148, 29)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun action(label: String, fill: Int = panelColor, foreground: Int = Color.WHITE,
                       onTap: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(foreground)
        background = shape(fill, 14)
        setPadding(dp(10), dp(13), dp(10), dp(13))
        isClickable = true
        isFocusable = true
        setOnClickListener { onTap() }
    }

    private fun addRow(parent: LinearLayout, vararg items: TextView) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        items.forEach { item ->
            row.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        parent.addView(row)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "VORKAN  /  CLIMB"
            textSize = 12f
            letterSpacing = 0.16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
        })
        header.addView(TextView(this).apply {
            text = "Tu muro.\nTus retos."
            textSize = 28f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        header.addView(TextView(this).apply {
            text = "DETECTA  ·  CREA  ·  ESCALA"
            textSize = 12f
            setTextColor(mutedColor)
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header)

        editor = HoldView(this).apply {
            background = shape(panelColor, 18)
        }
        status = TextView(this).apply {
            text = "Elige una foto de tu plafón para empezar."
            textSize = 12f
            setTextColor(mutedColor)
            setPadding(dp(20), dp(10), dp(20), dp(12))
            maxLines = 2
        }

        addRow(root, action("←  MIS ROCÓDROMOS") { showGymsHome() })
        addRow(root,
            action("＋  NUEVA FOTO", accentColor, backgroundColor) { choosePhotoSource() },
            action("▦  MIS BLOQUES") {
                if (activeCloudGymId != null) {
                    status.text = "Sincronizando bloques…"
                    syncCloudBlocks { showLibrary() }
                } else showLibrary()
            }
        )
        val subtitle = TextView(this).apply {
            text = "01   SELECCIONA LAS PRESAS"
            textSize = 11f
            letterSpacing = 0.09f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(mutedColor)
            setPadding(dp(20), dp(12), 0, dp(4))
        }
        root.addView(subtitle)
        addRow(root,
            action("●  SALIDA", Color.rgb(26, 69, 57), Color.rgb(105, 244, 165)) {
                editor.markColor = Color.GREEN
                status.text = "Salida seleccionada. Toca la presa para detectarla."
            },
            action("●  PRESA", Color.rgb(76, 65, 35), Color.rgb(255, 222, 119)) {
                editor.markColor = Color.YELLOW
                status.text = "Presa permitida seleccionada."
            },
            action("●  TOP", Color.rgb(82, 40, 50), Color.rgb(255, 143, 152)) {
                editor.markColor = Color.RED
                status.text = "Top seleccionado."
            }
        )
        val photoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), 0)
        }
        photoContainer.addView(editor, LinearLayout.LayoutParams(-1, -1))
        root.addView(photoContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        addRow(root, action("⛶  CREAR A PANTALLA COMPLETA", accentColor, backgroundColor) {
            showDraftFullscreen()
        })
        addRow(root,
            action("＋  AMPLIAR") {
                editor.zoomIn()
                status.text = "Imagen ampliada. Arrastra para desplazarte y toca la presa."
            },
            action("◉  LUZ DE PRESAS") {
                editor.spotlightEnabled = !editor.spotlightEnabled
                editor.invalidate()
                status.text = if (editor.spotlightEnabled)
                    "Resaltado activado: las presas marcadas quedan iluminadas."
                else "Resaltado desactivado: fotografía con brillo normal."
            }
        )
        addRow(root,
            action("⤢  FOTO COMPLETA") {
                editor.resetView()
                status.text = "Vista completa. Amplía con dos dedos para marcar."
            },
            action("↶  DESHACER") {
                editor.undoLastMark()
                status.text = "Última marca eliminada."
            },
            action("✕  LIMPIAR") {
                AlertDialog.Builder(this).setTitle("Borrar todas las marcas")
                    .setMessage("¿Quieres quitar las marcas de esta foto?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Borrar") { _, _ -> editor.clearMarks() }.show()
            }
        )
        root.addView(status)
        addRow(root, action("GUARDAR COMO NUEVO  →", accentColor, backgroundColor) { showSaveDialog() })
        editorScreen = root
        val alreadySignedIn = FirebaseApp.getApps(this).isNotEmpty() &&
            FirebaseAuth.getInstance().currentUser != null
        if (alreadySignedIn) showGymsHome() else showWelcome()
    }

    private fun showBrandIntroOnce() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(30), dp(24), dp(30))
            setBackgroundColor(backgroundColor)
        }
        splash.addView(VorkanEmblem(this), LinearLayout.LayoutParams(dp(190), dp(190)))
        splash.addView(TextView(this).apply {
            text = "VORKAN CLIMB"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(210, 213, 216))
            setPadding(0, dp(20), 0, dp(6))
        })
        splash.addView(TextView(this).apply {
            text = "TU PRÓXIMO MOVIMIENTO"
            letterSpacing = 0.17f
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
        })
        splash.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(splash)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        splash.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 1050L)
    }

    override fun onDestroy() {
        if (::editor.isInitialized) editor.releaseResources()
        super.onDestroy()
    }

    private fun showDraftFullscreen() {
        if (!editor.hasPhoto()) {
            status.text = "Primero elige una foto."
            return
        }
        if (editor.readOnly) {
            status.text = "Este bloque está terminado y no se puede editar."
            return
        }
        val parent = editor.parent as? android.view.ViewGroup ?: return
        val originalIndex = parent.indexOfChild(editor)
        parent.removeView(editor)
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        val title = TextView(this).apply {
            text = "CREANDO BLOQUE  ·  VORKAN"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(8))
        }
        frame.addView(title)
        frame.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        var restored = false
        fun restore() {
            if (restored) return
            restored = true
            (editor.parent as? android.view.ViewGroup)?.removeView(editor)
            parent.addView(editor, originalIndex, LinearLayout.LayoutParams(-1, -1))
            editor.resetView()
        }
        dialog.setOnDismissListener { restore() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                event.action == android.view.KeyEvent.ACTION_UP) {
                dialog.dismiss()
                true
            } else keyCode == android.view.KeyEvent.KEYCODE_BACK
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(12))
        }
        fun addControls(vararg views: TextView) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            views.forEach { v ->
                row.addView(v, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            controls.addView(row)
        }
        addControls(
            action("● SALIDA", Color.rgb(26, 69, 57), Color.rgb(105, 244, 165)) { editor.markColor = Color.GREEN },
            action("● PRESA", Color.rgb(76, 65, 35), Color.rgb(255, 222, 119)) { editor.markColor = Color.YELLOW },
            action("● TOP", Color.rgb(82, 40, 50), Color.rgb(255, 143, 152)) { editor.markColor = Color.RED }
        )
        // Compact toolbar: holds toggle off by tapping them again, so an
        // always-visible undo row is unnecessary. Keep essential tools.
        addControls(
            action("⤢ ENCAJAR") { editor.resetView() },
            action("◉ LUZ") {
                editor.spotlightEnabled = !editor.spotlightEnabled
                editor.invalidate()
            },
            action("↶ DESHACER") { editor.undoLastMark() }
        )
        addControls(
            action("← VOLVER") { dialog.dismiss() },
            action("GUARDAR BLOQUE →", accentColor, backgroundColor) {
                dialog.dismiss()
                showSaveDialog()
            }
        )
        val controlsScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(controls)
        }
        frame.addView(controlsScroll, LinearLayout.LayoutParams(-1, -2))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        dialog.setContentView(frame)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        dialog.window?.navigationBarColor = backgroundColor
        dialog.window?.statusBarColor = backgroundColor
    }

    private fun publishCloudBlock(data: JSONObject) {
        val gymId = activeCloudGymId ?: return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val id = data.optString("id")
        val photo = File(storage, data.optString("photo"))
        if (!photo.isFile) return
        val photoPath = "gyms/$gymId/blocks/$id.jpg"
        status.text = "Subiendo bloque al rocódromo…"
        FirebaseStorage.getInstance().reference.child(photoPath)
            .putFile(Uri.fromFile(photo)).addOnSuccessListener {
                val marks = data.optJSONArray("marks") ?: JSONArray()
                val markList = (0 until marks.length()).map { i ->
                    val item = marks.getJSONObject(i)
                    val map = mutableMapOf<String, Any>(
                        "x" to item.getDouble("x"), "y" to item.getDouble("y"),
                        "color" to item.getInt("color"), "radius" to item.optDouble("radius", -1.0))
                    item.optJSONArray("outline")?.let { outline ->
                        map["outline"] = (0 until outline.length()).map { j ->
                            val pair = outline.getJSONArray(j)
                            listOf(pair.getDouble(0), pair.getDouble(1))
                        }
                    }
                    map
                }
                val doc = mapOf(
                    "id" to id, "name" to data.optString("name"),
                    "grade" to data.optString("grade"), "kind" to "Bloque",
                    "photoPath" to photoPath, "marks" to markList,
                    "authorId" to user.uid,
                    "authorName" to (user.displayName ?: user.email ?: "Miembro"),
                    "createdAt" to FieldValue.serverTimestamp())
                FirebaseFirestore.getInstance().collection("gyms").document(gymId)
                    .collection("blocks").document(id).set(doc)
                    .addOnSuccessListener { status.text = "Bloque publicado para todos los miembros." }
                    .addOnFailureListener { e ->
                        status.text = "Guardado localmente. Error al publicar: ${e.localizedMessage}"
                    }
            }.addOnFailureListener { e ->
                status.text = "Guardado localmente. Error al subir foto: ${e.localizedMessage}"
            }
    }

    private fun syncCloudBlocks(onComplete: () -> Unit) {
        val gymId = activeCloudGymId ?: return onComplete()
        FirebaseFirestore.getInstance().collection("gyms").document(gymId)
            .collection("blocks").get().addOnSuccessListener { results ->
                val pending = results.documents.filter { doc ->
                    val id = doc.id
                    !File(storage, "$id.json").exists() || !File(storage, "$id.jpg").exists()
                }
                if (pending.isEmpty()) { onComplete(); return@addOnSuccessListener }
                var remaining = pending.size
                fun finished() { remaining--; if (remaining == 0) onComplete() }
                pending.forEach { doc ->
                    val id = doc.id
                    val path = doc.getString("photoPath") ?: run { finished(); return@forEach }
                    val target = File(storage, "$id.jpg")
                    FirebaseStorage.getInstance().reference.child(path)
                        .getFile(target).addOnSuccessListener {
                            try {
                                val marks = JSONArray()
                                (doc.get("marks") as? List<*>)?.forEach { mark ->
                                    marks.put(JSONObject(mark as Map<*, *>))
                                }
                                val data = JSONObject().put("id", id)
                                    .put("name", doc.getString("name"))
                                    .put("grade", doc.getString("grade"))
                                    .put("kind", "Bloque").put("photo", "$id.jpg")
                                    .put("marks", marks)
                                    .put("authorId", doc.getString("authorId"))
                                    .put("cloud", true)
                                File(storage, "$id.json").writeText(data.toString())
                            } catch (e: Exception) {
                                Toast.makeText(this, "Error de bloque: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                            finished()
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Error al descargar foto: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            finished()
                        }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "No se pudieron sincronizar bloques: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                onComplete()
            }
    }

    private fun showSaveDialog() {
        if (editor.readOnly) {
            status.text = "Este bloque está cerrado. Crea uno nuevo desde NUEVA FOTO."
            return
        }
        if (!editor.hasPhoto()) {
            status.text = "Primero elige una fotografía."
            return
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        val name = EditText(this).apply {
            hint = "Nombre del bloque"
            setSingleLine(true)
            setText(if (currentId == null) currentName else "")
        }
        val grade = EditText(this).apply {
            hint = "Grado Fontainebleau (ej. 6A+)"
            setSingleLine(true)
            setText(currentGrade)
        }
        fields.addView(name)
        fields.addView(grade)
        AlertDialog.Builder(this).setTitle("Guardar como bloque nuevo").setView(fields)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                try {
                    val id = UUID.randomUUID().toString()
                    val photoFile = "$id.jpg"
                    val temporary = File(storage, "$id.jpg.tmp")
                    val source = currentPhotoFile?.let { File(storage, it) }
                    if (source != null && source.isFile && source.length() > 0L) {
                        source.copyTo(temporary, overwrite = true)
                    } else {
                        temporary.outputStream().use { out -> editor.writePhoto(out) }
                    }
                    if (!temporary.isFile || temporary.length() == 0L) {
                        error("No se pudo guardar la fotografía")
                    }
                    if (!temporary.renameTo(File(storage, photoFile))) {
                        temporary.delete()
                        error("No se pudo guardar la fotografía")
                    }
                    val data = JSONObject().apply {
                        put("id", id)
                        put("name", name.text.toString().ifBlank { "Sin nombre" })
                        put("grade", grade.text.toString().ifBlank { "Sin grado" })
                        put("kind", "Bloque")
                        put("photo", photoFile)
                        put("marks", editor.exportMarks())
                    }
                    val tempData = File(storage, "$id.json.tmp")
                    tempData.writeText(data.toString())
                    if (!tempData.renameTo(File(storage, "$id.json"))) {
                        tempData.delete()
                        error("No se pudo guardar la ficha")
                    }
                    currentId = id
                    editor.readOnly = true
                    currentPhotoFile = photoFile
                    currentName = data.getString("name")
                    currentGrade = data.getString("grade")
                    status.text = "Guardado en el móvil: ${data.getString("name")}"
                    if (activeCloudGymId != null) publishCloudBlock(data)
                } catch (e: Exception) {
                    status.text = "No se pudo guardar: ${e.message}"
                }
            }.show()
    }

    private var libraryDialog: AlertDialog? = null

    private fun showLibrary() {
        val entries = storage.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { runCatching { JSONObject(it.readText()) }.getOrNull() }
            ?.filter { it.optString("kind", "Bloque") == "Bloque" }
            ?.sortedWith(compareBy<JSONObject> { gradeOrder(it.optString("grade")) }
                .thenBy { it.optString("name").lowercase() })
            ?: emptyList()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Mis bloques")
                .setMessage("Todavía no hay bloques guardados. Puedes crear uno o restaurar una copia de seguridad.")
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Restaurar copia") { _, _ ->
                    importBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                }.show()
            return
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val cards = mutableListOf<Pair<JSONObject, View>>()
        entries.forEach { data ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = shape(panelColor, 14)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { showBlockOptions(data) { libraryDialog?.dismiss() } }
            }
            val thumbnail = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = shape(Color.rgb(40, 53, 65), 10)
                clipToOutline = true
                val file = File(storage, data.optString("photo"))
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, options))
                }
            }
            card.addView(thumbnail, LinearLayout.LayoutParams(dp(82), dp(82)))
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
            }
            details.addView(TextView(this).apply {
                text = data.optString("name", "Sin nombre")
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 2
            })
            details.addView(TextView(this).apply {
                text = "BOULDER  ·  ${data.optString("grade", "Sin grado").uppercase()}"
                textSize = 12f
                letterSpacing = 0.05f
                setTextColor(accentColor)
                setPadding(0, dp(8), 0, 0)
            })
            details.addView(TextView(this).apply {
                text = "Abrir o eliminar"
                textSize = 11f
                setTextColor(mutedColor)
                setPadding(0, dp(5), 0, 0)
            })
            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            cards.add(data to card)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(list)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        panel.addView(TextView(this).apply {
            text = "VORKAN CLIMB  ·  ${entries.size} BLOQUES"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(22), dp(24), dp(22), dp(12))
        })
        val search = EditText(this).apply {
            hint = "Buscar por nombre o grado"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(mutedColor)
            background = shape(panelColor, 12)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), 0, dp(16), dp(12))
        })
        val empty = TextView(this).apply {
            text = "No hay bloques que coincidan con la búsqueda."
            textSize = 14f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(30), dp(18), dp(30))
            visibility = View.GONE
        }
        panel.addView(empty)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                var visibleCount = 0
                cards.forEach { (data, card) ->
                    val matches = data.optString("name").lowercase().contains(query) ||
                        data.optString("grade").lowercase().contains(query)
                    card.visibility = if (matches) View.VISIBLE else View.GONE
                    if (matches) visibleCount++
                }
                empty.visibility = if (visibleCount == 0) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addRow(panel,
            action("↑  EXPORTAR COPIA") { exportBackup.launch("escalada-bloques.zip") },
            action("↓  RESTAURAR") { importBackup.launch(arrayOf("application/zip", "application/octet-stream")) }
        )
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        libraryDialog = dialog
        dialog.setOnDismissListener { if (libraryDialog === dialog) libraryDialog = null }
        panel.addView(action("CERRAR BIBLIOTECA", panelColor, Color.WHITE) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(8), dp(16), dp(20))
        })
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }

    private fun gradeOrder(grade: String): Int {
        val normalized = grade.trim().uppercase()
        val match = Regex("^([3-9])([ABC])?([+])?$").matchEntire(normalized)
            ?: return Int.MAX_VALUE
        val number = match.groupValues[1].toInt()
        val letter = when (match.groupValues[2]) { "A" -> 0; "B" -> 2; "C" -> 4; else -> 0 }
        val plus = if (match.groupValues[3] == "+") 1 else 0
        return number * 10 + letter + plus
    }

    private fun showBlockOptions(data: JSONObject, onOpen: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(22), dp(20), dp(22), dp(16))
        }
        panel.addView(TextView(this).apply {
            text = data.optString("name", "Sin nombre")
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 2
        })
        panel.addView(TextView(this).apply {
            text = "BOULDER  ·  ${data.optString("grade", "Sin grado").uppercase()}"
            textSize = 13f
            setTextColor(accentColor)
            setPadding(0, dp(8), 0, dp(22))
        })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        val open = action("ABRIR BLOQUE  →", accentColor, backgroundColor) {
            dialog.dismiss()
            onOpen()
            openBlock(data)
            if (currentId == data.optString("id")) showBlockViewer(data)
        }
        panel.addView(open, LinearLayout.LayoutParams(-1, -2))
        val delete = action("ELIMINAR BLOQUE", panelColor, Color.rgb(255, 143, 152)) {
            dialog.dismiss()
            confirmDelete(data)
        }
        panel.addView(delete, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
        panel.addView(action("CANCELAR", backgroundColor, mutedColor) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun openBlock(data: JSONObject) {
        try {
            val filename = data.getString("photo")
            val bitmap = BitmapFactory.decodeFile(File(storage, filename).absolutePath)
                ?: error("Fotografía no disponible")
            editor.setPhoto(bitmap)
            editor.importMarks(data.optJSONArray("marks") ?: JSONArray())
            editor.readOnly = true
            currentId = data.getString("id")
            currentPhotoFile = filename
            currentName = data.optString("name")
            currentGrade = data.optString("grade")
            status.text = "Bloque terminado: $currentName · $currentGrade. Solo lectura."
        } catch (e: Exception) {
            status.text = "No se pudo abrir: ${e.message}"
        }
    }

    /** Immersive, read-only climbing view. Editing tools stay on the main screen. */
    private fun shareFinishedBlock(data: JSONObject, viewer: HoldView) {
        runCatching {
            val photoFile = File(storage, data.optString("photo"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Fotografía no disponible" }

            // Render a dedicated export at the photo's aspect ratio, not the
            // phone screen's aspect ratio. This avoids black letterbox bands.
            val longEdge = 1600f
            val factor = min(1f, longEdge / max(bounds.outWidth, bounds.outHeight))
            val photoWidth = max(1, (bounds.outWidth * factor).toInt())
            val photoHeight = max(1, (bounds.outHeight * factor).toInt())
            val headerHeight = max(120, (photoWidth * 0.17f).toInt())
            val image = Bitmap.createBitmap(photoWidth, photoHeight + headerHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                canvas.drawColor(Color.rgb(16, 20, 25))
                // Brand emblem in the header, outside the photo area.
                val emblemSize = (headerHeight * 0.72f).toInt()
                val emblemLeft = photoWidth - emblemSize - max(18, (photoWidth * 0.035f).toInt())
                val emblemTop = (headerHeight - emblemSize) / 2
                val emblem = androidx.core.content.ContextCompat.getDrawable(
                    this, R.drawable.vorkan_emblem
                )?.mutate()
                emblem?.setBounds(emblemLeft, emblemTop,
                    emblemLeft + emblemSize, emblemTop + emblemSize)
                emblem?.draw(canvas)
                val margin = max(18f, photoWidth * 0.035f)
                paint.color = accentColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = max(22f, photoWidth * 0.043f)
                canvas.drawText("VORKAN CLIMB", margin, headerHeight * 0.39f, paint)
                paint.color = Color.WHITE
                paint.textSize = max(19f, photoWidth * 0.037f)
                val name = data.optString("name", "Bloque").ifBlank { "Bloque" }
                val grade = data.optString("grade", "Sin grado").ifBlank { "Sin grado" }
                val label = "$name  ·  $grade"
                val available = max(20f, emblemLeft - margin * 2)
                val shown = paint.breakText(label, true, available, null)
                canvas.drawText(label.take(shown), margin, headerHeight * 0.78f, paint)

                canvas.save()
                // HoldView.onDraw uses drawColor(), which ignores translation.
                // Clip the photo viewport so it cannot paint over the header.
                canvas.clipRect(0f, headerHeight.toFloat(),
                    photoWidth.toFloat(), (headerHeight + photoHeight).toFloat())
                canvas.translate(0f, headerHeight.toFloat())
                viewer.drawForShare(canvas, photoWidth, photoHeight)
                canvas.restore()

                val folder = File(cacheDir, "shared-blocks").apply { mkdirs() }
                folder.listFiles()?.forEach { if (it.isFile) it.delete() }
                val file = File(folder, "vorkan-bloque.jpg")
                file.outputStream().use { output ->
                    check(image.compress(CompressFormat.JPEG, 92, output))
                }
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "VORKAN CLIMB · $name · $grade")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("Bloque VORKAN", uri)
                }
                startActivity(Intent.createChooser(intent, "Compartir bloque"))
            } finally {
                image.recycle()
            }
        }.onFailure {
            Toast.makeText(this, "No se pudo compartir: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showBlockViewer(data: JSONObject) {
        val bitmap = BitmapFactory.decodeFile(
            File(storage, data.optString("photo")).absolutePath
        ) ?: return
        val viewer = HoldView(this).apply {
            setPhoto(bitmap)
            importMarks(data.optJSONArray("marks") ?: JSONArray())
            readOnly = true
            spotlightEnabled = true
            setBackgroundColor(backgroundColor)
        }
        val overlay = android.widget.FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
            addView(viewer, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        val menuButton = TextView(this).apply {
            text = "⋮"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = shape(Color.argb(190, 25, 37, 49), 14)
            contentDescription = "Opciones del bloque"
        }
        overlay.addView(menuButton, android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.RIGHT).apply {
            setMargins(0, dp(12), dp(12), 0)
        })
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(overlay)
        dialog.window?.setLayout(-1, -1)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        menuButton.setOnClickListener {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(12))
                background = shape(panelColor, 22)
            }
            val menuDialog = AlertDialog.Builder(this).setView(panel).create()
            panel.addView(TextView(this).apply {
                text = "VORKAN CLIMB"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accentColor)
                setPadding(dp(8), 0, dp(8), dp(14))
            })
            fun addOption(label: String, callback: () -> Unit) {
                panel.addView(action(label) {
                    menuDialog.dismiss()
                    callback()
                }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
            }
            addOption("◉   LUZ DE PRESAS") {
                viewer.spotlightEnabled = !viewer.spotlightEnabled
                viewer.invalidate()
            }
            addOption("⤢   FOTO COMPLETA") { viewer.resetView() }
            addOption("↗   COMPARTIR BLOQUE") { shareFinishedBlock(data, viewer) }
            addOption("▦   MIS BLOQUES") {
                dialog.dismiss()
                showLibrary()
            }
            menuDialog.show()
            menuDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            menuDialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(40), -2)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun confirmDelete(data: JSONObject) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar bloque")
            .setMessage("¿Eliminar «${data.optString("name")}» y su fotografía? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                val id = data.optString("id")
                val metadata = File(storage, "$id.json")
                if (!metadata.delete()) {
                    status.text = "No se pudo eliminar el bloque."
                    return@setPositiveButton
                }
                File(storage, data.optString("photo")).delete()
                if (currentId == id) {
                    currentId = null
                    currentPhotoFile = null
                    currentName = ""
                    currentGrade = ""
                    editor.setPhoto(null)
                }
                status.text = "Bloque eliminado."
                Toast.makeText(this, "Bloque eliminado", Toast.LENGTH_SHORT).show()
            }.show()
    }

}

class HoldView(context: Context) : View(context) {
    private var photo: Bitmap? = null
    private val aiWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var ai: AIHoldSegmenter? = null
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transform = Matrix()
    private val inverse = Matrix()
    private data class Mark(val x: Float, val y: Float, val color: Int, val radius: Float, val outline: List<Pair<Float, Float>> = emptyList())
    private val marks = mutableListOf<Mark>()
    var markRadiusDp = 16f
    var spotlightEnabled = true
    var manualContourMode = false
    var readOnly = false
    private val drawingOutline = mutableListOf<Pair<Float, Float>>()
    var markColor: Int = Color.YELLOW
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var downAt = 0L
    private var moved = false
    private var multiTouch = false
    private var pinchStart = 0f
    private var zoomStart = 1f

    fun setPhoto(bitmap: Bitmap?) {
        detectionGeneration++
        detectedBoxes.clear()
        photo = bitmap
        marks.clear()
        drawingOutline.clear()
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    // The draft editor moves between the Activity and a full-screen Dialog.
    // Detaching it is temporary: shutting down its worker here permanently
    // breaks hold segmentation when the user taps a hold in full-screen.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    fun releaseResources() {
        if (!aiWorker.isShutdown) {
            aiWorker.execute { runCatching { ai?.close() }; ai = null }
            aiWorker.shutdown()
        }
    }

    private var detectionGeneration = 0
    private val detectedBoxes = mutableListOf<HoldBoxes.Box>()

    fun detectOffline(model: File, report: (String) -> Unit) {
        if (readOnly) { report("Bloque terminado: no se puede modificar."); return }
        val bitmap = photo
        if (bitmap == null) {
            report("Primero selecciona una fotografía.")
            return
        }
        val generation = ++detectionGeneration
        aiWorker.execute {
            runCatching {
                OfflineHoldDetector(model).use { detector -> detector.detect(bitmap) }
            }.onSuccess { boxes ->
                post {
                    if (generation == detectionGeneration && photo === bitmap) {
                        detectedBoxes.clear()
                        detectedBoxes.addAll(boxes)
                        invalidate()
                        report("Detectadas ${boxes.size} presas (cajas experimentales).")
                    }
                }
            }.onFailure { error ->
                post { if (generation == detectionGeneration) report("Error ONNX: ${error.message}") }
            }
        }
    }

    fun drawForShare(canvas: Canvas, outputWidth: Int, outputHeight: Int) {
        val bitmap = photo ?: return
        val savedWidth = width
        val savedHeight = height
        // Reuse the exact same contours and spotlight renderer, but with a
        // photo-sized viewport. The original view and its zoom stay untouched.
        val export = HoldView(context)
        try {
            export.setPhoto(bitmap)
            export.importMarks(exportMarks())
            export.readOnly = true
            export.spotlightEnabled = spotlightEnabled
            export.layout(0, 0, outputWidth, outputHeight)
            export.draw(canvas)
        } finally {
            export.releaseResources()
        }
    }

    fun photoIdentity(): Bitmap? = photo

    fun hasPhoto() = photo != null

    fun writePhoto(output: java.io.OutputStream) {
        check(checkNotNull(photo).compress(CompressFormat.JPEG, 88, output)) {
            "No se pudo codificar la fotografía"
        }
    }

    fun exportMarks(): JSONArray = JSONArray().apply {
        marks.forEach { mark ->
            put(JSONObject().put("x", mark.x.toDouble()).put("y", mark.y.toDouble())
                .put("color", mark.color).put("radius", mark.radius.toDouble()).apply {
                    if (mark.outline.isNotEmpty()) put("outline", JSONArray().apply {
                        mark.outline.forEach { (x, y) -> put(JSONArray().put(x.toDouble()).put(y.toDouble())) }
                    })
                })
        }
    }

    fun importMarks(data: JSONArray) {
        marks.clear()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            marks.add(Mark(item.optDouble("x").toFloat(), item.optDouble("y").toFloat(),
                item.optInt("color", Color.YELLOW), item.optDouble("radius", -1.0).toFloat(),
                item.optJSONArray("outline")?.let { arr ->
                    (0 until arr.length()).mapNotNull { j -> arr.optJSONArray(j)?.let { p ->
                        if (p.length() == 2) Pair(p.optDouble(0).toFloat(), p.optDouble(1).toFloat()) else null
                    } }
                } ?: emptyList()))
        }
        invalidate()
    }

    fun clearMarks() { if (!readOnly) { marks.clear(); invalidate() } }

    fun undoLastMark() { if (!readOnly && marks.isNotEmpty()) { marks.removeAt(marks.lastIndex); invalidate() } }

    fun zoomIn() {
        zoom = (zoom * 1.6f).coerceAtMost(12f)
        invalidate()
    }

    fun resetView() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(31, 42, 53))
        val bitmap = photo ?: return
        // Mostrar la fotografía completa sin recortes, conservando su proporción.
        val fit = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        transform.reset()
        transform.postScale(fit * zoom, fit * zoom)
        transform.postTranslate(
            (width - bitmap.width * fit * zoom) / 2 + offsetX,
            (height - bitmap.height * fit * zoom) / 2 + offsetY
        )
        brush.style = Paint.Style.FILL
        brush.color = Color.WHITE
        canvas.drawBitmap(bitmap, transform, brush)
        if (detectedBoxes.isNotEmpty()) {
            brush.style = Paint.Style.STROKE
            // Subtle detector overlay: thin, translucent lines leave holds visible.
            brush.strokeWidth = 0.8f * resources.displayMetrics.density
            brush.color = Color.argb(85, 0, 220, 225)
            for (box in detectedBoxes) {
                val corners = floatArrayOf(
                    box.left * bitmap.width, box.top * bitmap.height,
                    box.right * bitmap.width, box.bottom * bitmap.height
                )
                transform.mapPoints(corners)
                canvas.drawRect(corners[0], corners[1], corners[2], corners[3], brush)
            }
            brush.style = Paint.Style.FILL
        }
        if (spotlightEnabled && marks.isNotEmpty()) {
            // Dim the wall while keeping selected hold areas at their original brightness.
            val mask = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                marks.forEach { mark ->
                    val point = floatArrayOf(mark.x * bitmap.width, mark.y * bitmap.height)
                    transform.mapPoints(point)
                    if (mark.outline.size >= 3) addPath(outlinePath(mark.outline, bitmap))
                    else {
                        val radius = if (mark.radius > 0f) mark.radius * fit * zoom else 20f
                        addCircle(point[0], point[1], radius + 5f * resources.displayMetrics.density, Path.Direction.CW)
                    }
                }
            }
            brush.style = Paint.Style.FILL
            brush.color = Color.argb(165, 5, 11, 20)
            canvas.drawPath(mask, brush)
        }
        marks.forEach { mark ->
            val point = floatArrayOf(mark.x * bitmap.width, mark.y * bitmap.height)
            transform.mapPoints(point)
            brush.style = Paint.Style.STROKE
            // Selected holds remain distinguishable without hiding the hold surface.
            brush.strokeWidth = 0.75f * resources.displayMetrics.density
            brush.color = Color.argb(175, Color.red(mark.color), Color.green(mark.color), Color.blue(mark.color))
            if (mark.outline.size >= 3) canvas.drawPath(outlinePath(mark.outline, bitmap), brush)
            else {
                val radius = if (mark.radius > 0f) mark.radius * fit * zoom else 20f
                canvas.drawCircle(point[0], point[1], radius, brush)
            }
        }
        if (drawingOutline.size >= 2) {
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = 3f * resources.displayMetrics.density
            brush.color = markColor
            canvas.drawPath(outlinePath(drawingOutline, bitmap), brush)
        }
        brush.style = Paint.Style.FILL
    }

    private fun addOutlinePoint(event: MotionEvent, bitmap: Bitmap) {
        if (!transform.invert(inverse)) return
        val point = floatArrayOf(event.x, event.y)
        inverse.mapPoints(point)
        if (point[0] !in 0f..bitmap.width.toFloat() || point[1] !in 0f..bitmap.height.toFloat()) return
        val x = (point[0] / bitmap.width).coerceIn(0f, 1f)
        val y = (point[1] / bitmap.height).coerceIn(0f, 1f)
        val previous = drawingOutline.lastOrNull()
        if (previous == null || hypot((previous.first - x) * bitmap.width,
                (previous.second - y) * bitmap.height) >=
                3f * resources.displayMetrics.density / (min(width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height) * zoom)) {
            drawingOutline.add(Pair(x, y))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return try {
            handlePhotoTouch(event)
        } catch (error: Exception) {
            android.util.Log.e("VorkanHoldView", "Touch failed", error)
            android.widget.Toast.makeText(context,
                "Error al marcar la presa. Inténtalo de nuevo.",
                android.widget.Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun handlePhotoTouch(event: MotionEvent): Boolean {
        val bitmap = photo ?: return true
        if (manualContourMode && !readOnly) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawingOutline.clear()
                    addOutlinePoint(event, bitmap)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) addOutlinePoint(event, bitmap)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    addOutlinePoint(event, bitmap)
                    if (drawingOutline.size >= 6) {
                        val outline = drawingOutline.toList()
                        val x = outline.map { it.first }.average().toFloat()
                        val y = outline.map { it.second }.average().toFloat()
                        marks.add(Mark(x, y, markColor, -1f, outline))
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    drawingOutline.clear()
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    drawingOutline.clear()
                    invalidate()
                    return true
                }
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                downAt = System.currentTimeMillis()
                moved = false
                multiTouch = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                moved = true
                if (event.pointerCount >= 2) {
                    pinchStart = distance(event)
                    zoomStart = zoom
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    if (pinchStart > 0f) zoom = (zoomStart * distance(event) / pinchStart).coerceIn(1f, 12f)
                    multiTouch = true
                } else if (!multiTouch) {
                    val deltaX = event.x - previousX
                    val deltaY = event.y - previousY
                    if (hypot(deltaX, deltaY) > 12f) {
                        moved = true
                        offsetX += deltaX
                        offsetY += deltaY
                        previousX = event.x
                        previousY = event.y
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> { multiTouch = true; return true }
            MotionEvent.ACTION_UP -> {
                if (!readOnly && !multiTouch && !moved && System.currentTimeMillis() - downAt < 1200) {
                    if (transform.invert(inverse)) {
                        val point = floatArrayOf(event.x, event.y)
                        inverse.mapPoints(point)
                        if (point[0] in 0f..bitmap.width.toFloat() && point[1] in 0f..bitmap.height.toFloat()) {
                            val radiusOnPhoto = markRadiusDp * resources.displayMetrics.density / (min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height) * zoom)
                            val nx = point[0] / bitmap.width
                            val ny = point[1] / bitmap.height
                            val selectedColor = markColor
                            // Detected holds are directly selectable, without requiring MagicTouch.
                            // Choose the smallest box containing the tap to avoid selecting
                            // a neighboring hold when detector boxes overlap.
                            val selectedBox = detectedBoxes
                                .filter { nx >= it.left && nx <= it.right &&
                                    ny >= it.top && ny <= it.bottom }
                                .minByOrNull { (it.right - it.left) * (it.bottom - it.top) }
                            // A previously marked contour can also be deselected
                            // without requiring a matching detector box.
                            val touchedMark = marks.indexOfLast { mark ->
                                if (mark.outline.size >= 3) {
                                    var inside = false
                                    val pts = mark.outline
                                    var j = pts.lastIndex
                                    for (i in pts.indices) {
                                        val a = pts[i]
                                        val b = pts[j]
                                        if ((a.second > ny) != (b.second > ny) &&
                                            nx < (b.first - a.first) * (ny - a.second) /
                                                (b.second - a.second) + a.first) inside = !inside
                                        j = i
                                    }
                                    inside
                                } else {
                                    hypot(nx - mark.x, ny - mark.y) < 0.016f
                                }
                            }
                            if (touchedMark >= 0) {
                                marks.removeAt(touchedMark)
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                invalidate()
                                return true
                            }
                            if (selectedBox != null) {
                                // Tap a selected detected hold again to remove it,
                                // regardless of its current start/hold/finish color.
                                val existing = marks.indexOfFirst { mark ->
                                    mark.x >= selectedBox.left && mark.x <= selectedBox.right &&
                                    mark.y >= selectedBox.top && mark.y <= selectedBox.bottom
                                }
                                if (existing >= 0) {
                                    marks.removeAt(existing)
                                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    invalidate()
                                    return true
                                }
                                val outline = listOf(
                                    Pair(selectedBox.left, selectedBox.top),
                                    Pair(selectedBox.right, selectedBox.top),
                                    Pair(selectedBox.right, selectedBox.bottom),
                                    Pair(selectedBox.left, selectedBox.bottom)
                                )
                                val provisional = Mark(selectedBox.centerX, selectedBox.centerY,
                                    selectedColor, radiusOnPhoto, outline)
                                marks.add(provisional)
                                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                invalidate()
                                // Refine only this selected hold in the background. The
                                // rectangle remains usable if segmentation fails.
                                aiWorker.execute {
                                    val cropFraction = (maxOf(
                                        selectedBox.right - selectedBox.left,
                                        selectedBox.bottom - selectedBox.top
                                    ) * 3f).coerceIn(0.10f, 0.36f)
                                    // Reject outlines that wander outside the detected hold.
                                    // Try the hold centre first, then the actual touch if needed.
                                    fun validContour(points: List<Pair<Float, Float>>): Boolean {
                                        if (points.size < 4) return false
                                        val marginX = (selectedBox.right - selectedBox.left) * 0.35f
                                        val marginY = (selectedBox.bottom - selectedBox.top) * 0.35f
                                        if (!points.all { (x, y) ->
                                                x in (selectedBox.left - marginX)..(selectedBox.right + marginX) &&
                                                    y in (selectedBox.top - marginY)..(selectedBox.bottom + marginY)
                                            }) return false
                                        val xs = points.map { it.first }
                                        val ys = points.map { it.second }
                                        val contourW = xs.maxOrNull()!! - xs.minOrNull()!!
                                        val contourH = ys.maxOrNull()!! - ys.minOrNull()!!
                                        // Tiny fragments should not replace a usable rectangle.
                                        return contourW >= (selectedBox.right - selectedBox.left) * 0.18f &&
                                            contourH >= (selectedBox.bottom - selectedBox.top) * 0.18f
                                    }
                                    val centered = runCatching {
                                        if (ai == null) ai = AIHoldSegmenter(context.applicationContext)
                                        ai!!.select(bitmap, selectedBox.centerX, selectedBox.centerY, cropFraction)
                                    }.getOrNull().orEmpty()
                                    val refined = if (validContour(centered)) centered else {
                                        runCatching {
                                            ai!!.select(bitmap, nx, ny, cropFraction)
                                        }.getOrNull().orEmpty()
                                    }
                                    if (validContour(refined)) {
                                        post {
                                            if (photo === bitmap) {
                                                val index = marks.indexOfFirst { it === provisional }
                                                if (index >= 0) {
                                                    marks[index] = provisional.copy(outline = refined)
                                                    invalidate()
                                                }
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                            aiWorker.execute {
                                val result = runCatching {
                                    if (ai == null) ai = AIHoldSegmenter(context.applicationContext)
                                    ai!!.select(bitmap, nx, ny)
                                }
                                post {
                                    if (photo !== bitmap) return@post
                                    val outline = result.getOrNull().orEmpty()
                                    if (outline.isNotEmpty()) {
                                        marks.add(Mark(nx, ny, selectedColor, radiusOnPhoto, outline))
                                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        invalidate()
                                    } else {
                                        android.widget.Toast.makeText(context,
                                            if (result.isFailure) "Error al procesar la imagen: " +
                                                (result.exceptionOrNull()?.message ?: "desconocido")
                                            else "No se ha obtenido un contorno fiable. Prueba otro punto.",
                                            android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun outlinePath(outline: List<Pair<Float, Float>>, bitmap: Bitmap): Path = Path().apply {
        outline.forEachIndexed { i, (x, y) ->
            val p = floatArrayOf(x * bitmap.width, y * bitmap.height)
            transform.mapPoints(p)
            if (i == 0) moveTo(p[0], p[1]) else lineTo(p[0], p[1])
        }
        close()
    }

    // Offline color-connected segmentation. Conservative by design: reject uncertain regions.
    private fun detectHold(bitmap: Bitmap, x: Float, y: Float, hintRadius: Float): List<Pair<Float, Float>> {
        val scale = min(1f, 480f / max(bitmap.width, bitmap.height))
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            val sx = (x * w / bitmap.width).toInt().coerceIn(0, w - 1)
            val sy = (y * h / bitmap.height).toInt().coerceIn(0, h - 1)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            val seed = pixels[sy * w + sx]
            val visited = BooleanArray(w * h)
            val queue = IntArray(w * h)
            val selected = ArrayList<Int>()
            val center = sy * w + sx
            queue[0] = center
            visited[center] = true
            var head = 0
            var tail = 1
            val maxRadius = max(12f, hintRadius * scale * 3.2f).coerceAtMost(min(w, h) * .32f)
            val limit = min((w * h * .09f).toInt(), (PI * maxRadius * maxRadius).toInt())
            fun difference(a: Int, b: Int): Int {
                val dr = Color.red(a) - Color.red(b)
                val dg = Color.green(a) - Color.green(b)
                val db = Color.blue(a) - Color.blue(b)
                return dr * dr + dg * dg + db * db
            }
            val threshold = 58 * 58
            while (head < tail && selected.size <= limit) {
                val index = queue[head++]
                selected.add(index)
                val px = index % w
                val py = index / w
                val neighbors = intArrayOf(index - 1, index + 1, index - w, index + w)
                neighbors.forEachIndexed { direction, next ->
                    if ((direction == 0 && px == 0) || (direction == 1 && px == w - 1) ||
                        (direction == 2 && py == 0) || (direction == 3 && py == h - 1) ||
                        visited[next]) return@forEachIndexed
                    visited[next] = true
                    val nx = next % w - sx
                    val ny = next / w - sy
                    if (nx * nx + ny * ny > maxRadius * maxRadius) return@forEachIndexed
                    if (difference(pixels[next], seed) <= threshold &&
                        difference(pixels[next], pixels[index]) <= 38 * 38) {
                        queue[tail++] = next
                    }
                }
            }
            if (selected.size < 12 || selected.size > limit || head < tail) return emptyList()
            val minX = selected.minOf { it % w }
            val maxX = selected.maxOf { it % w }
            val minY = selected.minOf { it / w }
            val maxY = selected.maxOf { it / w }
            val boxArea = (maxX - minX + 1) * (maxY - minY + 1)
            if (boxArea < 16 || selected.size.toFloat() / boxArea < .28f ||
                max(maxX - minX, maxY - minY) > maxRadius * 1.8f) return emptyList()
            val bins = 48
            val radial = FloatArray(bins) { -1f }
            selected.forEach { index ->
                val dx = index % w - sx
                val dy = index / w - sy
                val angle = atan2(dy.toDouble(), dx.toDouble())
                val bin = (((angle + PI) / (2 * PI) * bins).toInt()).coerceIn(0, bins - 1)
                val distance = hypot(dx.toFloat(), dy.toFloat())
                radial[bin] = max(radial[bin], distance)
            }
            if (radial.count { it > 0f } < bins * .85f) return emptyList()
            val positive = radial.filter { it > 0f }
            if (positive.maxOrNull()!! > positive.sorted()[positive.size / 2] * 2.6f) return emptyList()
            return (0 until bins).mapNotNull { i ->
                if (radial[i] <= 0f) null else {
                    val angle = -PI + (i + .5) * 2 * PI / bins
                    val r = radial[i] + 1.5f
                    Pair(((sx + cos(angle) * r) / w).toFloat().coerceIn(0f, 1f),
                        ((sy + sin(angle) * r) / h).toFloat().coerceIn(0f, 1f))
                }
            }
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    private fun distance(event: MotionEvent): Float =
        hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
}

/** Temporary vector emblem inspired by the VORKAN mountain/lightning mark.
 * Replace with the original transparent logo asset when supplied. */
class VorkanEmblem(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        canvas.save()
        canvas.translate((width-side)/2f, (height-side)/2f)
        canvas.scale(side/200f, side/200f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 9f
        paint.color = Color.rgb(184, 190, 194)
        canvas.drawCircle(100f, 100f, 86f, paint)
        val mountain = Path().apply {
            moveTo(22f, 133f); lineTo(101f, 46f); lineTo(136f, 84f)
            lineTo(147f, 76f); lineTo(180f, 124f)
        }
        paint.color = Color.rgb(71, 93, 107)
        paint.strokeWidth = 11f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(mountain, paint)
        val bolt = Path().apply {
            moveTo(120f, 73f); lineTo(74f, 121f); lineTo(101f, 121f)
            lineTo(76f, 180f); lineTo(147f, 105f); lineTo(116f, 105f); close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(247, 148, 29)
        canvas.drawPath(bolt, paint)
        canvas.restore()
    }
}
