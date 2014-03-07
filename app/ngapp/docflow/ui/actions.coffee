module = angular.module 'docflow.ui.actions', ['docflow.config', 'ng']

module.constant 'docflowActionInProgressShowAfterDelayTime', 2000
module.constant 'docflowActionDoneHideDelayTime', 3000

module.directive 'docflowActionsContainer',
  ['$docflowActions', '$compile', '$animate',
  (($docflowActions, $compile, $animate) ->
    return {
      restrict: 'A'
      scope: true
      controller: (($scope) ->
        @contentTransclude = null
        @contentElement = null
        return)
      link: (($scope, element, attrs, ctrl) ->
        messageScope = null
        currentElement = null
        options = $scope.$eval attrs.docflowActionsContainer
        container =
          errorOnly: !!options?.errorOnly
          setAction: ((action) ->
            $scope.action = action
            return)
        $docflowActions.add container
        $scope.$watch 'action.message', ((message) ->
          if messageScope
            $animate.leave(currentElement)
            currentElement = null
            messageScope.$destroy()
            messageScope = null
          if message
            messageScope = $scope.$new()
            ctrl.contentTransclude messageScope, ((clone) ->
#              clone.html(message)
              currentElement = clone
              currentElement.html message
              $compile(currentElement.contents())(messageScope)
              $animate.enter(currentElement, null, ctrl.contentElement);
              return)
          return)
        $scope.$on '$destroy', (->
          $docflowActions.remove container
          return)
        return)
  })]

module.directive 'docflowActionsContainerContent',
  ['$docflowActions', '$compile',
  (($docflowActions, $compile) ->
    return {
      restrict: 'EA'
      require: '^docflowActionsContainer'
      transclude: 'element'
      link: (($scope, element, attrs, container, $transclude) ->
        container.contentTransclude = $transclude
        container.contentElement = element
        return)
  })]

module.directive 'docflowActionsAutohide',
  ['$docflowActions', 'docflowActionDoneHideDelayTime', '$timeout',
  (($docflowActions, docflowActionDoneHideDelayTime, $timeout) ->
    return {
      restrict: 'EA'
      scope: true
      link: (($scope, element, attrs) ->
        $scope.action.showClose = false
        $timeout (->
          $scope.action.hide()
          return), docflowActionDoneHideDelayTime
        return)
  })]

module.factory '$docflowActions',
  ['$docflowConfig', '$docflowUtils', 'docflowActionInProgressShowAfterDelayTime', 'docflowActionDoneHideDelayTime', '$rootScope', '$timeout',
  (($docflowConfig, $docflowUtils, docflowActionInProgressShowAfterDelayTime, docflowActionDoneHideDelayTime, $rootScope, $timeout) ->

    currentAction = null
    currentContainer = null
    containers = []

    selectContainer = ((action) ->
      if currentAction != action and currentContainer
        currentContainer.setAction null
        currentContainer = null
      currentAction = action
      if containers.length > 0
        container = containers[0]
        if action.state != 'failed'
          for cont in containers
            if not cont.errorOnly
              container = cont
              break
        if currentContainer != container
          if currentContainer
            currentContainer.setAction null
          (currentContainer = container).setAction action
      return)

    preserveActionResultOverUIStateTransitionCount = 0

    return {

      containers: containers

      Action: (class Action
        constructor: ((docId, name, promise) ->
          if not docId
            throw Error 'docId argument is missing'
          if not name
            throw Error 'name argument is missing'
          if not promise
            throw Error 'promise argument is missing'

          sid = $docflowUtils.splitFullId docId
          title = $docflowConfig.docs[sid.docType]?.actions?[name]?.title
          message = $docflowConfig.messages['actionProgress']?.replace '{{title}}', title

          @name = name
          @title = title || name
          @state = 'progress'
          @message = null
          @promise = promise
          @showClose = false
          if message
            @forceShowMessage = forceShowMessage = (=>
              if @state == 'progress'
                @message = message
              return)
            $timeout forceShowMessage, docflowActionInProgressShowAfterDelayTime

          promise.then(
            ((doneMessage) =>
              @state = 'done'
              @message = if doneMessage
                @showClose = true
                @message = doneMessage
              else
                $timeout (=>
                  @hide()
                  return), docflowActionDoneHideDelayTime
                @message = $docflowConfig.messages['actionDone']?.replace '{{title}}', @title
              return),
            ((failedMessage) =>
              @state = 'failed'
              @showClose = true
              @message = if failedMessage
                @message = failedMessage
              else
                @message = $docflowConfig.messages['actionFailed']?.replace '{{title}}', @title
              return)
          )

          return)

        hide: (->
          if currentAction == @
            currentContainer?.setAction(null)
          return))

      setPreserveActionResultOverUIStateTransition: (->
        preserveActionResultOverUIStateTransitionCount = 4 # Hack: It's because for some reasons ui-router create initialized editor form TWICE.
        return)

      add: ((container) ->
        containers.unshift container
        if currentAction and currentContainer
          currentContainer.setAction null
          currentContainer = null
        if preserveActionResultOverUIStateTransitionCount > 0 and currentAction
          if --preserveActionResultOverUIStateTransitionCount == 0
            currentContainer = container
            container.setAction currentAction
        return)

      remove: ((container) ->
        if (p = containers.indexOf container) >= 0
          containers.splice p, 1
          if currentContainer == container
            currentContainer = null
            if currentAction.state != 'failed'
              selectContainer currentAction
        return)

      send: ((action) ->
        selectContainer action
        action.promise.then((-> selectContainer(action)), (-> selectContainer(action)))
        return)
  })]

