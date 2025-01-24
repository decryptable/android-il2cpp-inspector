package com.decryptable.il2cppinspector

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.Objects
import java.util.concurrent.TimeUnit

open class MainActivity : AppCompatActivity() {
    private var textViewLog: TextView? = null
    private var alertDialog: AlertDialog? = null
    private var buttonInject: Button? = null
    private var targetPackageInput: TextInputEditText? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        DynamicColors.applyToActivitiesIfAvailable(this.application)
        textViewLog = findViewById(R.id.log_shell)
        buttonInject = findViewById(R.id.button_inject)
        targetPackageInput = findViewById(R.id.target_package)

        alertDialog = MaterialAlertDialogBuilder(this)
            .setPositiveButton(getString(R.string.close)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }.create()
        alertDialog!!.setTitle(getString(R.string.app_name))


        buttonInject!!.setOnClickListener(View.OnClickListener {
            val targetPackage = targetPackageInput!!.getText().toString()
            if (targetPackage.isEmpty()) {
                alertDialog!!.setMessage(getString(R.string.target_package_required))
                alertDialog!!.show()
                return@OnClickListener
            }

            textViewLog!!.text = getString(R.string.log_banner)

            buttonInject!!.setEnabled(false)
            targetPackageInput!!.setEnabled(false)
            Thread {
                injectInspectorToPackage(targetPackage)
            }.start()
        })
    }

    private fun injectInspectorToPackage(packageTarget: String) {
        Shell.getShell { shell: Shell ->
            if (shell.isRoot) {
                val libInjector = File(applicationInfo.nativeLibraryDir + "/libinjector.so")
                val libAgent = File(applicationInfo.nativeLibraryDir + "/libagent.so")
                val libInspector = File(applicationInfo.nativeLibraryDir + "/libinspector.so")

                val agent = File(applicationInfo.dataDir)

                Log.i(TAG, "Agent directory: " + agent.absolutePath)

                if (!libAgent.exists()) {
                    Log.e(TAG, "Agent doesn't exist: " + libAgent.absolutePath)
                    textViewLog!!.text = getString(R.string.file_not_exist, libAgent.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                if (!libInjector.exists()) {
                    Log.e(TAG, "Injector doesn't exist: " + libInjector.absolutePath)
                    textViewLog!!.text = getString(R.string.file_not_exist, libInjector.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                if (!libInspector.exists()) {
                    Log.e(TAG, "Inspector doesn't exist: " + libInspector.absolutePath)
                    textViewLog!!.text = getString(R.string.file_not_exist, libInspector.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                val newLibAgent = setExecutable(libAgent)

                if (newLibAgent.isEmpty()) {
                    Log.e(TAG, "Failed set to executable for : " + libAgent.absolutePath)
                    textViewLog!!.text = getString(R.string.failed_set_to_executable, libAgent.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                val newLibInjectorPath = setExecutable(libInjector)

                if (newLibInjectorPath.isEmpty()) {
                    Log.e(TAG, "Failed set to executable for : " + libInjector.absolutePath)
                    textViewLog!!.text = getString(R.string.failed_set_to_executable, libInjector.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                val newLibInspectorPath = setExecutable(libInspector)

                if (newLibInspectorPath.isEmpty()) {
                    Log.e(TAG, "Failed set to executable for : " + libInspector.absolutePath)
                    textViewLog!!.text = getString(R.string.failed_set_to_executable, libInspector.name)
                    buttonInject!!.isEnabled = true
                    targetPackageInput!!.isEnabled = true
                    return@getShell
                }

                try {
                    Shell.cmd("am force-stop $packageTarget").exec()
                    startActivity(
                        Objects.requireNonNull(
                            packageManager.getLaunchIntentForPackage(packageTarget)
                        )
                    )
                } catch (ignored: Exception) {
                }

                val command =
                    "$newLibInjectorPath -p $(pidof $packageTarget) -s /data/local/tmp/libagent.so -e"


                Log.d(TAG, "Executing command: $command")

                val errMsg: MutableList<String> = ArrayList()

                val callbackList: List<String> = object : CallbackList<String>() {
                    override fun onAddElement(s: String) {
                        var oldLog = s
                        oldLog = oldLog.replace("I: ", "").replace("E: ", "").trim { it <= ' ' }
                        val newLog = oldLog
                        errMsg.add(newLog)
                        runOnUiThread {
                            textViewLog!!.text =
                                getString(R.string.log, textViewLog!!.text, newLog).trimIndent()
                        }
                    }
                }

                try {
                    val delaySeconds = 5;
                Toast.makeText(this, getString(R.string.please_wait, delaySeconds), Toast.LENGTH_LONG).show();

                    Toast.makeText(this, getString(R.string.please_wait, delaySeconds), Toast.LENGTH_SHORT).show()
                    for (second in delaySeconds downTo 0) {
                        TimeUnit.SECONDS.sleep(second.toLong())
                        Toast.makeText(this, getString(R.string.please_wait, second), Toast.LENGTH_SHORT).show()
                    }
                } catch (ignored: InterruptedException) {
                }

                Shell.cmd(command).to(callbackList).submit { out: Shell.Result ->
                    if (!out.isSuccess) {
                        val lastError = if (errMsg.isNotEmpty()) {
                            errMsg[errMsg.size - 1].trim { it <= ' ' }
                        } else {
                            getString(R.string.package_not_found, packageTarget)
                        }
                        runOnUiThread {
                            alertDialog!!.setMessage(lastError)
                            alertDialog!!.show()
                        }

                        Toast.makeText(applicationContext, lastError, Toast.LENGTH_LONG).show()
                    }
                    runOnUiThread {
                        buttonInject!!.isEnabled = true
                        targetPackageInput!!.isEnabled = true
                    }
                }
            } else {
                Log.e(TAG, "No root access available")

                if (alertDialog!!.isShowing) {
                    alertDialog!!.dismiss()
                }

                alertDialog!!.setMessage(getString(R.string.no_root_access_available))
                alertDialog!!.show()
                buttonInject!!.isEnabled = true
                targetPackageInput!!.isEnabled = true
            }
        }
    }

    private fun setExecutable(file: File): String {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: " + file.absolutePath)
            return ""
        }

        val localDataTmpPath = File("/data/local/tmp/" + file.name)

        val resultCopy =
            Shell.cmd("pkill -f libinjector.so; pkill -f libinspector.so; cp " + file.absolutePath + " " + localDataTmpPath.absolutePath)
                .exec()

        if (!resultCopy.isSuccess) {
            Log.e(TAG, "Failed to copy file " + file.name)
            return ""
        }

        val command = "chmod 755 " + localDataTmpPath.absolutePath
        try {
            val result = Shell.cmd(command).exec()

            return if (result.isSuccess) {
                localDataTmpPath.absolutePath
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change permissions for: " + file.absolutePath, e)
            return ""
        }
    }

    companion object {
        private const val TAG = "IL2CPP-Inspector"
    }
}
