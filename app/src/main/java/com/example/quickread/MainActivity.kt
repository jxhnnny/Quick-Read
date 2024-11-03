@file:Suppress("DEPRECATION")

package com.example.quickread

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log.d
import android.util.Log.e
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

private const val CAMERA_PERMISSION_REQUEST_CODE = 101
//test
class MainActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etSurname: EditText
    private lateinit var etIdNumber: EditText
    private lateinit var etCellNumber: EditText
    private lateinit var etEmail: EditText
    private lateinit var etOccupation: EditText
    private lateinit var etAddress: EditText
    private lateinit var etPin: EditText
    private lateinit var btnGenerateQR: Button
    private lateinit var ivQRCode: ImageView
    private lateinit var buttonScan: Button
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = FirebaseFirestore.getInstance()

        etName = findViewById(R.id.name)
        etSurname = findViewById(R.id.surname)
        etIdNumber = findViewById(R.id.id_number)
        etCellNumber = findViewById(R.id.cell_number)
        etEmail = findViewById(R.id.email)
        etOccupation = findViewById(R.id.occupation)
        etAddress = findViewById(R.id.address)
        etPin = findViewById(R.id.pin)
        btnGenerateQR = findViewById(R.id.buttonGenerateQR)
        ivQRCode = findViewById(R.id.imageViewQRCode)
        buttonScan = findViewById(R.id.buttonScanQR)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }

        btnGenerateQR.setOnClickListener {
            if (listOf(etName, etSurname, etIdNumber, etCellNumber, etEmail, etOccupation, etAddress, etPin).any { it.text.isEmpty() }) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uniqueID = UUID.randomUUID().toString()
            val pinCode = etPin.text.toString()
            val userData = UserData(
                name = etName.text.toString(),
                surname = etSurname.text.toString(),
                idNumber = etIdNumber.text.toString(),
                cellNumber = etCellNumber.text.toString(),
                email = etEmail.text.toString(),
                occupation = etOccupation.text.toString(),
                address = etAddress.text.toString(),
                pin = pinCode
            )

            db.collection("QRData").document(uniqueID).set(userData)
                .addOnSuccessListener {
                    d("QRGenerator", "Data successfully saved to Firestore")
                    val qrCodeBitmap = generateQRCode(uniqueID)
                    ivQRCode.setImageBitmap(qrCodeBitmap)
                    if (qrCodeBitmap != null) {
                        saveQRCodeToGallery(qrCodeBitmap)
                    }
                }
                .addOnFailureListener { e ->
                    e("QRGenerator", "Error saving data", e)
                    Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show()
                }
        }
         fun displayResultInFloatingBox(message: String) {
            AlertDialog.Builder(this)
                .setTitle("QR Code Data")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }


        val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
                if (intentResult != null && intentResult.contents != null) {
                    val scannedContent = intentResult.contents

                    val appGeneratedQrPrefix = "https://jxhnnny.github.io/quickread.github.io/?id="
                    if (scannedContent.startsWith(appGeneratedQrPrefix)) {
                        val uniqueID = scannedContent.removePrefix(appGeneratedQrPrefix)
                        promptForPin(uniqueID) // Ask for PIN only for app-generated QR codes
                    } else {
                        // Display content directly for non-app-generated QR codes
                        displayResultInFloatingBox("Scanned Content:\n$scannedContent")
                    }
                } else {
                    Toast.makeText(this, "No QR code found", Toast.LENGTH_SHORT).show()
                }
            }
        }


        buttonScan.setOnClickListener {
            val scanOptions = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan QR code")
                setBeepEnabled(true)
                setOrientationLocked(false)
            }
            scannerLauncher.launch(scanOptions.createScanIntent(this))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQRCode(uniqueID: String): Bitmap? {
        return try {
            val secureDataUrl = "https://jxhnnny.github.io/quickread.github.io/?id=$uniqueID"
            val size = 512
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix: BitMatrix = qrCodeWriter.encode(secureDataUrl, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun saveQRCodeToGallery(bitmap: Bitmap) {
        val filename = "QRCode_${System.currentTimeMillis()}.png"
        val fos: OutputStream?
        try {
            fos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/QuickRead")
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                resolver.openOutputStream(imageUri!!)
            } else {
                val imagesDir = getExternalFilesDir(null)?.absoluteFile ?: throw Exception("External storage not available")
                val image = File(imagesDir, filename)
                FileOutputStream(image)
            }
            fos?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Toast.makeText(this, "QR code saved to gallery", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Error saving QR code", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForPin(uniqueID: String) {
        fun displayResultInFloatingBox(message: String) {
            AlertDialog.Builder(this)
                .setTitle("QR Code Data")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        db.collection("QRData").document(uniqueID).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val storedPin = document.getString("pin")
                    val pinEditText = EditText(this)
                    pinEditText.hint = "Enter PIN"

                    AlertDialog.Builder(this)
                        .setTitle("Security Check")
                        .setMessage("Enter the PIN to view the QR code data")
                        .setView(pinEditText)
                        .setPositiveButton("OK") { dialog, _ ->
                            val enteredPin = pinEditText.text.toString()
                            if (enteredPin == storedPin) {
                                displayResultInFloatingBox(document.data.toString())
                            } else {
                                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    Toast.makeText(this, "No data found for this QR code.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                e("QRScanner", "Error retrieving data", it)
                Toast.makeText(this, "Error retrieving data.", Toast.LENGTH_SHORT).show()
            }
    }


    data class UserData(
        val name: String,
        val surname: String,
        val idNumber: String,
        val cellNumber: String,
        val email: String,
        val occupation: String,
        val address: String,
        val pin: String
    )
}
