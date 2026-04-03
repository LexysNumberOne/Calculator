package org.fossify.math.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.grantland.widget.AutofitHelper
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.APP_ICON_IDS
import org.fossify.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import org.fossify.commons.helpers.LICENSE_EVALEX
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MEDIUM_ALPHA_INT
import org.fossify.commons.models.FAQItem
import org.fossify.math.R
import org.fossify.math.databases.CalculatorDatabase
import org.fossify.math.databinding.ActivityMainBinding
import org.fossify.math.dialogs.HistoryDialog
import org.fossify.math.extensions.config
import org.fossify.math.extensions.updateViewColors
import org.fossify.math.helpers.CALCULATOR_STATE
import org.fossify.math.helpers.Calculator
import org.fossify.math.helpers.CalculatorImpl
import org.fossify.math.helpers.DIVIDE
import org.fossify.math.helpers.HistoryHelper
import org.fossify.math.helpers.MINUS
import org.fossify.math.helpers.MULTIPLY
import org.fossify.math.helpers.PERCENT
import org.fossify.math.helpers.PLUS
import org.fossify.math.helpers.POWER
import org.fossify.math.helpers.ROOT
import org.fossify.math.helpers.getDecimalSeparator

class MainActivity : SimpleActivity(), Calculator {
    private var storedTextColor = 0
    private var vibrateOnButtonPress = true
    private var saveCalculatorState: String = ""
    private lateinit var calc: CalculatorImpl

    private val binding by viewBinding(ActivityMainBinding::inflate)
    
    // Easter Egg: Secret sequence tracking
    private val secretSequence = mutableListOf<String>()
    private val targetSequence = listOf("1", PERCENT, POWER, "8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched("org.fossify.math")
        
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomSystem = listOf(binding.mainNestedScrollview))
        setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar)

        if (savedInstanceState != null) {
            saveCalculatorState = savedInstanceState.getCharSequence(CALCULATOR_STATE)?.toString() ?: ""
        }

        calc = CalculatorImpl(
            calculator = this,
            context = applicationContext,
            calculatorState = saveCalculatorState
        )
        
        binding.calculator?.apply {
            btnPlus.setOnClickOperation(PLUS)
            btnMinus.setOnClickOperation(MINUS)
            btnMultiply.setOnClickOperation(MULTIPLY)
            btnDivide.setOnClickOperation(DIVIDE)
            btnPercent.setOnClickOperation(PERCENT)
            btnPower.setOnClickOperation(POWER)
            btnRoot.setOnClickOperation(ROOT)
            btnMinus.setOnLongClickListener { calc.turnToNegative() }
            btnClear.setVibratingOnClickListener { 
                secretSequence.clear()
                calc.handleClear() 
            }
            btnClear.setOnLongClickListener {
                secretSequence.clear()
                calc.handleReset()
                true
            }

            getButtonIds().forEach { button ->
                button.setVibratingOnClickListener { view ->
                    val key = when (view.id) {
                        R.id.btn_0 -> "0"
                        R.id.btn_1 -> "1"
                        R.id.btn_2 -> "2"
                        R.id.btn_3 -> "3"
                        R.id.btn_4 -> "4"
                        R.id.btn_5 -> "5"
                        R.id.btn_6 -> "6"
                        R.id.btn_7 -> "7"
                        R.id.btn_8 -> "8"
                        R.id.btn_9 -> "9"
                        else -> ""
                    }
                    if (key.isNotEmpty()) {
                        checkEasterEgg(key)
                    }
                    calc.numpadClicked(view.id)
                }
            }

            btnEquals.setVibratingOnClickListener { 
                secretSequence.clear()
                calc.handleEquals() 
            }
            formula.setOnLongClickListener { copyToClipboard(false) }
            result.setOnLongClickListener { copyToClipboard(true) }
            AutofitHelper.create(result)
            AutofitHelper.create(formula)
            updateViewColors(calculatorHolder, getProperTextColor())
        }
        
        storeStateVariables()
        setupDecimalButton()
        checkAppOnSDCard()
    }

    private fun checkEasterEgg(key: String) {
        val nextExpectedIndex = secretSequence.size
        if (nextExpectedIndex < targetSequence.size && targetSequence[nextExpectedIndex] == key) {
            secretSequence.add(key)
            if (secretSequence.size == targetSequence.size) {
                showAppSearchDialog()
                secretSequence.clear()
            }
        } else {
            secretSequence.clear()
            // If the current key is the start of the sequence, add it back
            if (targetSequence[0] == key) {
                secretSequence.add(key)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar)
        setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar)
        
        if (storedTextColor != config.textColor) {
            binding.calculator?.calculatorHolder?.let { updateViewColors(it, getProperTextColor()) }
        }

        if (config.preventPhoneFromSleeping) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setupDecimalButton()
        vibrateOnButtonPress = config.vibrateOnButtonPress

        binding.calculator?.apply {
            arrayOf(
                btnPercent, btnPower, btnRoot, btnClear, btnReset, btnDivide, btnMultiply, btnPlus,
                btnMinus, btnEquals, btnDecimal
            ).forEach {
                it.background = ResourcesCompat.getDrawable(
                    resources, org.fossify.commons.R.drawable.pill_background, theme
                )
                it.background?.alpha = MEDIUM_ALPHA_INT
            }

            arrayOf(btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9).forEach {
                it.background = ResourcesCompat.getDrawable(
                    resources, org.fossify.commons.R.drawable.pill_background, theme
                )
                it.background?.alpha = LOWER_ALPHA_INT
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        if (config.preventPhoneFromSleeping) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            CalculatorDatabase.destroyInstance()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(CALCULATOR_STATE, calc.getCalculatorStateJson().toString())
    }

    private fun setupOptionsMenu() {
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.history -> showHistory()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.unit_converter -> launchUnitConverter()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.mainToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
        }
    }

    private fun checkHaptic(view: View) {
        if (vibrateOnButtonPress) {
            view.performHapticFeedback()
        }
    }

    private fun showHistory() {
        HistoryHelper(this).getHistory {
            if (it.isEmpty()) {
                toast(R.string.history_empty)
            } else {
                HistoryDialog(this, it, calc)
            }
        }
    }

    private fun launchUnitConverter() {
        hideKeyboard()
        startActivity(Intent(applicationContext, UnitConverterPickerActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(
            Intent(applicationContext, SettingsActivity::class.java).apply {
                putIntegerArrayListExtra(APP_ICON_IDS, getAppIconIDs())
            }
        )
    }

    private fun launchAbout() {
        val licenses = LICENSE_AUTOFITTEXTVIEW or LICENSE_EVALEX

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(
                title = org.fossify.commons.R.string.faq_1_title_commons,
                text = org.fossify.commons.R.string.faq_1_text_commons
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_4_title_commons,
                text = org.fossify.commons.R.string.faq_4_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = "1.4.0", // Fixed manual version
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    private fun getButtonIds(): Array<TextView> = binding.calculator?.run {
        arrayOf(btnDecimal, btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9)
    } ?: emptyArray()

    private fun copyToClipboard(copyResult: Boolean): Boolean {
        var value = binding.calculator?.formula?.value
        if (copyResult) {
            value = binding.calculator?.result?.value
        }

        return if (value.isNullOrEmpty()) {
            false
        } else {
            copyToClipboard(value)
            true
        }
    }

    override fun showNewResult(value: String, context: Context) {
        binding.calculator?.result?.text = value
    }

    override fun showNewFormula(value: String, context: Context) {
        binding.calculator?.formula?.text = value
    }

    private fun setupDecimalButton() {
        binding.calculator?.btnDecimal?.text = getDecimalSeparator()
    }

    private fun View.setVibratingOnClickListener(callback: (view: View) -> Unit) {
        setOnClickListener {
            callback(it)
            checkHaptic(it)
        }
    }

    private fun View.setOnClickOperation(operation: String) {
        setVibratingOnClickListener {
            checkEasterEgg(operation)
            calc.handleOperation(operation)
        }
    }

    // Yasin's Unshakable App Search Dialog
    private fun showAppSearchDialog() {
        val editText = EditText(this)
        editText.hint = "Type app name (e.g. termux)"
        
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(48, 16, 48, 16)
        editText.layoutParams = params
        container.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Launch App")
            .setView(container)
            .setPositiveButton("Search") { _, _ ->
                val query = editText.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchAndLaunchApp(query)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchAndLaunchApp(query: String) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val allApps = pm.queryIntentActivities(mainIntent, 0)
        Log.d("AppPicker", "Total apps found: ${allApps.size}")

        // Filter by label OR package name
        val filteredApps = allApps.filter { 
            val label = it.loadLabel(pm).toString()
            val pkg = it.activityInfo.packageName
            label.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
        }
        
        Log.d("AppPicker", "Filtered apps count for '$query': ${filteredApps.size}")

        when {
            filteredApps.isEmpty() -> toast("\"$query\" not found.")
            filteredApps.size == 1 -> {
                val pkg = filteredApps[0].activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    toast("Could not launch $pkg")
                }
            }
            else -> {
                // If multiple results, let user pick
                val appNames = filteredApps.map { it.loadLabel(pm).toString() }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Which $query?")
                    .setItems(appNames) { _, which ->
                        val pkg = filteredApps[which].activityInfo.packageName
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            toast("Could not launch $pkg")
                        }
                    }
                    .show()
            }
        }
    }
}
