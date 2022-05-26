package com.example.android.architecture.blueprints.todoapp.tasks

import androidx.lifecycle.SavedStateHandle
import com.example.android.architecture.blueprints.todoapp.ADD_EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.DELETE_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.EDIT_RESULT_OK
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import com.example.android.architecture.blueprints.todoapp.util.frp.StateChange
import com.example.android.architecture.blueprints.todoapp.util.frp.stateProducer
import com.example.android.architecture.blueprints.todoapp.util.frp.toStateChangeStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

sealed class Action {
    object Refresh : Action()

    object ClearCompletedTasks : Action()

    data class ShowEditResultMessage(
        val result: Int
    ) : Action()

    data class SetFilter(
        val requestType: TasksFilterType
    ) : Action()

    data class SetTaskCompletion(
        val task: Task,
        val completed: Boolean
    ) : Action()

    object SnackBarMessageShown : Action()

}

fun tasksStateProducer(
    scope: CoroutineScope,
    tasksRepository: TasksRepository,
    savedStateHandle: SavedStateHandle
) = stateProducer<Action, TasksUiState>(
    scope = scope,
    initialState = TasksUiState(isLoading = true),
    actionTransform = { actionStream ->
        actionStream.toStateChangeStream {
            when (val type = type()) {
                is Action.ClearCompletedTasks -> type.flow.clearCompletedTasksStateChanges(
                    tasksRepository = tasksRepository
                )
                is Action.Refresh -> type.flow.flatMapLatest {
                    refreshStateChanges(tasksRepository = tasksRepository)
                }
                is Action.SetFilter -> type.flow.filterChangeStateChanges(
                    savedStateHandle = savedStateHandle
                )
                is Action.SetTaskCompletion -> type.flow.taskCompletionStateChanges(
                    tasksRepository = tasksRepository
                )
                is Action.ShowEditResultMessage -> type.flow.editResultMessageStateChanges()
                is Action.SnackBarMessageShown -> type.flow.snackbarMessageStateChanges()
            }
        }
    }
)

private fun Flow<Action.SetFilter>.filterChangeStateChanges(
    savedStateHandle: SavedStateHandle
): Flow<StateChange<TasksUiState>> =
    distinctUntilChanged()
        .flatMapLatest { (requestType) ->
            savedStateHandle[TASKS_FILTER_SAVED_STATE_KEY] = requestType
            savedStateHandle.getStateFlow(
                key = TASKS_FILTER_SAVED_STATE_KEY,
                initialValue = TasksFilterType.ALL_TASKS
            )
        }
        .map {
            StateChange {
                copy(
                    filteringUiInfo = when (it) {
                        TasksFilterType.ALL_TASKS -> {
                            FilteringUiInfo(
                                R.string.label_all, R.string.no_tasks_all,
                                R.drawable.logo_no_fill
                            )
                        }
                        TasksFilterType.ACTIVE_TASKS -> {
                            FilteringUiInfo(
                                R.string.label_active, R.string.no_tasks_active,
                                R.drawable.ic_check_circle_96dp
                            )
                        }
                        TasksFilterType.COMPLETED_TASKS -> {
                            FilteringUiInfo(
                                R.string.label_completed, R.string.no_tasks_completed,
                                R.drawable.ic_verified_user_96dp
                            )
                        }
                    }
                )
            }
        }

private fun Flow<Action.ShowEditResultMessage>.editResultMessageStateChanges(
): Flow<StateChange<TasksUiState>> =
    mapLatest { (result) ->
        StateChange {
            copy(userMessage = when (result) {
                EDIT_RESULT_OK -> R.string.successfully_saved_task_message
                ADD_EDIT_RESULT_OK -> R.string.successfully_added_task_message
                DELETE_RESULT_OK -> R.string.successfully_deleted_task_message
                else -> null
            })
        }
    }

private fun Flow<Action.SnackBarMessageShown>.snackbarMessageStateChanges(
): Flow<StateChange<TasksUiState>> =
    mapLatest {
        StateChange {
            copy(userMessage = null)
        }
    }

private fun Flow<Action.SetTaskCompletion>.taskCompletionStateChanges(
    tasksRepository: TasksRepository
): Flow<StateChange<TasksUiState>> =
    distinctUntilChanged()
        .mapLatest { (task, completed) ->
            if (completed) tasksRepository.completeTask(task)
            else tasksRepository.activateTask(task)

            StateChange {
                copy(
                    userMessage = when (completed) {
                        true -> R.string.task_marked_complete
                        false -> R.string.task_marked_active
                    }
                )
            }
        }

private fun Flow<Action.ClearCompletedTasks>.clearCompletedTasksStateChanges(
    tasksRepository: TasksRepository
): Flow<StateChange<TasksUiState>> = flatMapLatest {
    tasksRepository.clearCompletedTasks()

    flow {
        emit(StateChange {
            copy(userMessage = R.string.completed_tasks_cleared)
        })
        emitAll(
            refreshStateChanges(
                tasksRepository = tasksRepository
            )
        )
    }
}


private fun refreshStateChanges(
    tasksRepository: TasksRepository
): Flow<StateChange<TasksUiState>> = flow {
    emit(StateChange { copy(isLoading = true) })
    tasksRepository.refreshTasks()
    emit(StateChange { copy(isLoading = false) })
}