package me.saket.press.shared.sync.git

import com.badoo.reaktive.completable.andThen
import com.badoo.reaktive.completable.asObservable
import com.badoo.reaktive.completable.completableFromFunction
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.ObservableWrapper
import com.badoo.reaktive.observable.combineLatest
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.ofType
import com.badoo.reaktive.observable.publish
import com.badoo.reaktive.observable.startWithValue
import com.badoo.reaktive.observable.switchMap
import com.badoo.reaktive.observable.withLatestFrom
import com.badoo.reaktive.observable.wrap
import io.ktor.client.HttpClient
import me.saket.press.shared.settings.Setting
import me.saket.press.shared.sync.git.NewGitRepositoryEvent.NameTextChanged
import me.saket.press.shared.sync.git.NewGitRepositoryEvent.SubmitClicked
import me.saket.press.shared.sync.git.NewGitRepositoryPresenter.SubmitResult.Idle
import me.saket.press.shared.sync.git.NewGitRepositoryPresenter.SubmitResult.Ongoing
import me.saket.press.shared.sync.git.service.GitHostService
import me.saket.press.shared.sync.git.service.NewGitRepositoryInfo
import me.saket.press.shared.ui.Navigator
import me.saket.press.shared.ui.Presenter
import me.saket.press.shared.util.isLetterOrDigit2
import me.saket.press.shared.sync.git.NewGitRepositoryEvent as Event
import me.saket.press.shared.sync.git.NewGitRepositoryUiModel as Model

class NewGitRepositoryPresenter(
  private val screenKey: NewGitRepositoryScreenKey,
  private val httpClient: HttpClient,
  private val navigator: Navigator,
  authToken: (GitHost) -> Setting<GitHostAuthToken>,
) : Presenter<Event, Model, Nothing>() {

  private val gitHost: GitHost get() = screenKey.gitHost
  private val gitHostService: GitHostService get() = gitHost.service(httpClient)
  private val authToken: Setting<GitHostAuthToken> = authToken(gitHost)

  override fun defaultUiModel() = Model(
    repoUrlPreview = "",
    errorMessage = null,
    isLoading = false
  )

  override fun uiModels(): ObservableWrapper<Model> {
    return viewEvents().publish { events ->
      val repoUrls = events
        .ofType<NameTextChanged>()
        .map { gitHost.newRepoUrl(screenKey.user.name, sanitize(it.name)) }

      combineLatest(repoUrls, handleSubmits(events)) { repoUrl, submitResult ->
        Model(
          repoUrlPreview = repoUrl,
          isLoading = submitResult == Ongoing,
          errorMessage = null
        )
      }
    }.wrap()
  }

  private fun sanitize(string: String): String {
    return buildString {
      for (char in string) {
        if (char.isLetterOrDigit2()) {
          append(char)
        } else if (lastOrNull() != '-') {
          append('-')
        }
      }
    }
  }

  private fun handleSubmits(events: Observable<Event>): Observable<SubmitResult> {
    val repoNames = events
      .ofType<NameTextChanged>()
      .map { sanitize(it.name) }

    return events.ofType<SubmitClicked>()
      .withLatestFrom(repoNames, ::Pair)
      .switchMap { (event, repoName) ->
        val newRepo = NewGitRepositoryInfo(name = repoName, private = event.privateRepo)
        gitHostService
          .createNewRepo(authToken.get()!!, newRepo)
          .andThen(completableFromFunction {
            navigator.goBack()
          })
          .asObservable<SubmitResult>()
          .startWithValue(Ongoing)
      }
      .startWithValue(Idle)
  }

  enum class SubmitResult {
    Idle,
    Ongoing,
    Failed
  }

  fun interface Factory {
    fun create(
      screenKey: NewGitRepositoryScreenKey
    ): NewGitRepositoryPresenter
  }
}
