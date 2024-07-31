package com.pico.otg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pico.otg.databinding.ActivityMainBinding
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.FileSystemFactory
import me.jahnen.libaums.core.fs.UsbFileOutputStream
import me.jahnen.libaums.core.fs.fat16.Fat16FileSystemCreator
import java.io.File
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {

    private val ACTION_USB_PERMISSION: String = "com.pico.otg" + ".USB_PERMISSION"
    private var usbPermissionGranted = false
    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    lateinit var otgDeviceListView: ListView
    lateinit var otgDeviceArrList: ArrayList<String>
    lateinit var otgDeviceAdapter: ArrayAdapter<String?>
    lateinit var otgDeviceFileListView: ListView
    lateinit var otgDeviceFileArrList: ArrayList<String>
    lateinit var otgDeviceFileAdapter: ArrayAdapter<String?>
    private var isDeviceInitialized: Boolean = false
    private var uploadButton: FloatingActionButton? = null
    private val GET_CONTENT = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        otgDeviceArrList = ArrayList()
        otgDeviceFileArrList = ArrayList()
        setupUsb()
    }

    /*private fun searchDevice() {
        baseContext?.let {
            val devices = UsbMassStorageDevice.getMassStorageDevices(it)
            for (device in devices) {
                device.init()

                val currentFs = device.partitions[0].fileSystem
                val root = currentFs.rootDirectory

                val files = root.listFiles()
                for (file in files) {
                    Log.d(TAG, file.name)
                    if (file.isDirectory) {
                        Log.d(TAG, "" + file.length)
                    }
                }

                val file = File("/storage/emulated/0/Download/picow_blink.uf2")
                if(file.exists()) {
                    val fileSize = file.length().toInt()
                    val fileName = file.name
                    val byteArray = ByteArray(fileSize)

                    try {
                        var fis = FileInputStream(file)
                        fis.read(byteArray)

                        val newFile = root.createFile(fileName)
                        val os = UsbFileOutputStream(newFile)
                        os.write(byteArray)
                        os.close()
                    } catch(e:Exception) {
                        var errorTxt = "Ensure permissions are enabled. " +
                                "Go to Settings -> Privacy -> Permissions Manager -> Files & Media " +
                                "-> Search & Select APP -> Select 'Allow management of all files'"
                        showDialog(baseContext, errorTxt)
                        Log.d(TAG, "error " + e.message)
                    }
                }
                device.close()
            }
        }
    }*/

    // BroadcastReceiver to detect USB device attachment
    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                usbPermissionGranted =
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (!usbPermissionGranted)
                    usbPermissionGranted = true

                //searchDevice()
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    usbDevice = device
                    otgDeviceArrList.add(device.deviceName)
                    otgDeviceAdapter.notifyDataSetChanged();

                    val m_permissionIntent = PendingIntent.getBroadcast(
                        baseContext,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    usbManager!!.requestPermission(device, m_permissionIntent)
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null && device == usbDevice) {
                    otgDeviceArrList.remove(device.deviceName)
                    otgDeviceAdapter.notifyDataSetChanged();
                    usbDevice = null

                    otgDeviceFileArrList.removeAll(otgDeviceFileArrList)
                    otgDeviceFileAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    // setup usb connections and register fat 16 file system & on click event handlers
    private fun setupUsb() {
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        val filter: IntentFilter = IntentFilter(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)

        FileSystemFactory.registerFileSystem(Fat16FileSystemCreator(), FileSystemFactory.DEFAULT_PRIORITY)

        // Check for existing connected devices
        val deviceList = usbManager!!.deviceList
        if (deviceList.isNotEmpty()) {
            for (device in deviceList.values) {
                if (device.interfaceCount > 0) {
                    usbDevice = device
                    otgDeviceArrList.add(device.deviceName)
                    break
                }
            }
        }

        otgDeviceListView = findViewById(R.id.UsbOTGList)
        otgDeviceFileListView = findViewById(R.id.UsbOTGFileList)
        uploadButton = findViewById(R.id.upload)

        otgDeviceAdapter = ArrayAdapter<String?>(
            this@MainActivity,
            android.R.layout.simple_list_item_1,
            otgDeviceArrList as List<String?>
        )

        otgDeviceFileAdapter = ArrayAdapter<String?>(
            this@MainActivity,
            android.R.layout.simple_list_item_1,
            otgDeviceFileArrList as List<String?>
        )

        otgDeviceListView.adapter = otgDeviceAdapter
        otgDeviceFileListView.adapter = otgDeviceFileAdapter

        otgDeviceListView.setOnItemClickListener { parent, view, position, id ->
            val m_permissionIntent = PendingIntent.getBroadcast(
                baseContext,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            usbManager!!.requestPermission(usbDevice, m_permissionIntent)

            listFiles()
        }

        uploadButton?.setOnClickListener(View.OnClickListener {
            if(usbDevice == null && otgDeviceArrList.size > 0) {
                listFiles()
                showUploadDialog()
            } else if(usbDevice != null) {
                showUploadDialog()
            }
        })
    }

    private fun showUploadDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("*/*");
        startActivityForResult(intent, GET_CONTENT);
    }

    // upload file to pico
    private fun uploadFile(fileName: String, byteArray: ByteArray) {

        val devices = UsbMassStorageDevice.getMassStorageDevices(baseContext)
        for (device in devices) {
            if(!isDeviceInitialized)
                device.init()

            val currentFs = device.partitions[0].fileSystem
            val root = currentFs.rootDirectory

            try {
                val newFile = root.createFile(fileName)
                val os = UsbFileOutputStream(newFile)
                os.write(byteArray)
                os.close()
            } catch(e:Exception) {
                var errorTxt = e.message.toString()
                var toast = Toast.makeText(baseContext, errorTxt, Toast.LENGTH_LONG);
                toast.show();
                //Log.d(Tag, "error " + e.message)
            }
            device.close()
        }
    }

    // get the selected file
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode === GET_CONTENT) {
            val fileUri: Uri? = data?.data
            val fileUriStr = fileUri.toString()

            var fileName = "test.uf2"
            var byteArray: ByteArray? = null
            val fileStr : String = fileUriStr.substring(fileUriStr.indexOf("file://") + 7,
                fileUriStr.indexOf("?size="))
            try {
                if(fileStr != null) {
                    val file = File(fileStr)
                    if(file.exists()) {
                        val fileSize = file.length().toInt()
                        fileName = file.name
                        byteArray = ByteArray(fileSize)
                        var fis = FileInputStream(file)
                        fis.read(byteArray)
                    }
                }
            } finally {
                if(byteArray == null) {
                    if (fileUri != null) {
                        byteArray = getContentResolver().openInputStream(fileUri)?.readBytes()
                    }
                }
            }

            if(byteArray != null) {
                uploadFile(fileName, byteArray)
            } else {
                var toast = Toast.makeText(baseContext, "Error parsing file", Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }

    // list existing files
    private fun listFiles() {
        baseContext?.let {
            val devices = UsbMassStorageDevice.getMassStorageDevices(it)
            for (device in devices) {
                if(!isDeviceInitialized)
                    device.init()

                val currentFs = device.partitions[0].fileSystem
                val root = currentFs.rootDirectory

                val files = root.listFiles()
                otgDeviceFileArrList.removeAll(otgDeviceFileArrList)
                for (file in files) {
                    otgDeviceFileArrList.add(file.name)
                }
                otgDeviceFileAdapter.notifyDataSetChanged()
            }
        }
    }

    // close devices
    override fun onDestroy() {
        super.onDestroy()

        if(isDeviceInitialized) {
            for (device in  UsbMassStorageDevice.getMassStorageDevices(baseContext)) {
                device.close()
            }
            isDeviceInitialized = false
        }
    }
}