package com.austinv11.nextflow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

@State(
    name = "NextflowSettings",
    storages = [Storage("nextflow-lsp.xml")]
)
@Service(Service.Level.PROJECT)
class NextflowSettings : PersistentStateComponent<NextflowSettings.State> {

    data class State(
        var errorReportingMode: String = "warnings"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): NextflowSettings =
            project.getService(NextflowSettings::class.java)
    }
}