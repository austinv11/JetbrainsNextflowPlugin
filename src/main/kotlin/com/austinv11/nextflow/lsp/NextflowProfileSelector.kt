package com.austinv11.nextflow.lsp

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

internal class NextflowProfileSelector(
    initialProfiles: List<String>,
    private val selectionChanged: () -> Unit
) : JPanel(BorderLayout()) {
    private val selected = LinkedHashSet(initialProfiles)
    private var available = emptyList<String>()
    private val link = DropDownLink("Select profiles") { createPopup() }

    val selectedProfiles: List<String> get() = selected.toList()
    val hasProfiles: Boolean get() = available.isNotEmpty() || selected.isNotEmpty()
    val text: String get() = link.text

    init {
        isOpaque = false
        add(link, BorderLayout.WEST)
        updatePresentation()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Swing can call this override before the child link has been constructed.
        components.forEach { it.isEnabled = enabled }
    }

    fun setAvailableProfiles(profiles: List<String>) {
        available = profiles.distinct()
        updatePresentation()
    }

    internal fun createProfileList() = CheckBoxList<String>().apply {
        // Keep removed profiles visible until deselected so refresh never silently changes the selection.
        for (profile in (available + selected).distinct()) {
            addItem(profile, if (profile in available) profile else "$profile (unavailable)", profile in selected)
        }
        fixedCellHeight = JBUI.scale(28)
        visibleRowCount = model.size.coerceIn(1, 8)
        ListSpeedSearch.installOn(this) { it.text }
        setCheckBoxListListener { index, checked ->
            val profile = getItemAt(index)
            if (checked) selected.add(profile) else selected.remove(profile)
            updatePresentation()
            selectionChanged()
        }
    }

    internal fun clearProfiles(list: CheckBoxList<String>) {
        if (selected.isEmpty()) return
        selected.clear()
        list.allItems.forEach { list.setItemSelected(it, false) }
        list.repaint()
        updatePresentation()
        selectionChanged()
    }

    internal fun createPopup(): JBPopup {
        val list = createProfileList()
        val content = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(JBScrollPane(list).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
            add(ActionLink("Clear selection") { clearProfiles(list) }, BorderLayout.SOUTH)
            preferredSize = JBUI.size(320, list.visibleRowCount * 28 + 56)
        }
        // A component popup stays open when a checkbox changes, unlike a menu item popup.
        return JBPopupFactory.getInstance().createComponentPopupBuilder(content, list)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .setAdText("Type to search · Space to toggle · Esc to close")
            .createPopup()
    }

    private fun updatePresentation() {
        link.text = when {
            !hasProfiles -> "No profiles available"
            selected.isEmpty() -> "Select profiles"
            selected.size <= 2 -> selected.joinToString()
            else -> "${selected.take(2).joinToString()} +${selected.size - 2}"
        }
        link.toolTipText = when {
            !hasProfiles -> "No profiles are defined in nextflow.config or its included configs."
            selected.isEmpty() -> "Select one or more profiles. The preview updates automatically."
            else -> "Applied in selection order: ${selected.joinToString()}. The preview updates automatically."
        }
        isEnabled = hasProfiles
    }
}
