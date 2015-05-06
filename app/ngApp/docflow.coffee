module = angular.module 'docflow', ['docflow.ui.client', 'docflow.ui.utils', 'docflow.config', 'ui.bootstrap.modal']

module.run [
  '$docflow', '$state', '$rootScope',
  (($docflow, $state, $rootScope) ->
    $rootScope.$docflow = $docflow
    $rootScope.$state = $state
    return)]

module.factory '$docflow', [
  '$docflowClient', '$docflowConfig', '$docflowUtils', '$state', '$location', '$modal', '$window', '$q', '$log',
  (($docflowClient, $docflowConfig, $docflowUtils, $state, $location, $modal, $window, $q, $log) ->

    editorConstructor = null

    return {

      # This to resolve circular dependency between $docflow and $docflowEditor
      _setEditor: ((editor) ->
        editorConstructor = editor
        return)

      config: $docflowConfig

      # id - document full id or just docType, in case of service actions or 'create'
      # name - action name
      # options:
      #  - doc - action incoming doc
      #  - r - document revision, required for actions involing 'update'
      #  - t - template, to render action 'doc'. Default: 'list'
      #  - tr - template, to render action 'result'. Default: same as 't'
      # Note: This action will add to options:
      #  - params - action parameters
      action: ((id, name, options) ->
        sid = $docflowUtils.splitFullId id
        doc = $docflowConfig.docs[sid.docType]
        if not doc
          throw Error "$docflow.action: Id '#{id}': Unknown type '#{sid.docType}'."
        action = doc.actions[name]
        if not action
          throw Error "$docflow.action: Name '#{name}': No such action in type '#{sid.docType}'."
        if action.service
          if sid.id
            throw Error "$docflow.action: Action '#{sid.docType}.#{name}': Service action requires type instead of id '#{id}'."
        else
          if not sid.id
            throw Error "$docflow.action: Action '#{sid.docType}.#{name}': Non-service action requires id instead of type '#{id}'."
        if options
          for optName, val of options
            switch optName
              when 'params' then continue
              when 'doc' then continue
              when 'r' then continue
              when 't' then continue
              when 'tr' then continue
              else $log.error "$docflow.action: Unexpected parameter '#{optName}'."
        else
          options = {}

        deferred = $q.defer()

        doAction = ((options) ->
          def = $q.defer()
          $docflowClient.action(id, name, options)
          .then(
            ((r) ->
              def.resolve(r)
              return),
            ((r) ->
              def.reject(r)
              return),
            ((r) ->
              def.notify(r)
              return)
          )
          return def.promise)

        doConfirm = null
        paramsDialog = null

        if action.confirm
          doConfirm = ((options, fromParams) ->
            $modal.open(
              templateUrl: "/tmpl/#{$docflowConfig.templateBase}/confirmation?#{$docflowConfig.tmplParams}"
              backdrop: if fromParams then false else 'static'
              controller: ['$scope', (($scope) ->
                $scope.question = action.confirm
                $scope.$on '$stateChangeStart', (->
                  doConfirm.close()
                  return)
                return)]
            ).result
            .then(
              ((r) ->
                doAction(options).then(
                  ((r) ->
                    deferred.resolve(r)
                    return),
                  ((r) ->
                    deferred.reject(r)
                    return),
                  ((r) ->
                    deferred.notify(r)
                    return))
                return),
              ((r) ->
                if not paramsDialog
                  deferred.reject('Cancelled in confimation dialog')
                return)
            )
            return)

        if action.params and not options.params
          params = angular.copy action.params
          paramsDialog = $modal.open(
            templateUrl: "/tmpl/doc/#{sid.docType}?b=#{$docflowConfig.templateBase}&t=params&a=#{name}&#{$docflowConfig.tmplParams}"
            backdrop: 'static'
            controller: ['$scope', (($scope) ->
              $scope.item = params
              $scope.ok = (->
                if not options
                  options = {}
                options.params = $docflowUtils.buildDocumentUpdate($scope.item)
                if doConfirm
                  doConfirm options, true
                else
                  doAction(options).then(
                    ((r) ->
                      deferred.resolve(r)
                      return),
                    ((r) ->
                      # nothing
                      return))
                return)
              $scope.$on '$stateChangeStart', (->
                paramsDialog.close()
                return)
              return)]
          )

          deferred.promise.then(
            ((r) ->
              paramsDialog.close(r)
              return),
            ((r) ->
              paramsDialog.close(r)
              return)
          )

        else if doConfirm
          doConfirm(options, false)
        else
          doAction(options).then(
            ((r) ->
              deferred.resolve(r)
              return),
            ((r) ->
              deferred.reject(r)
              return),
            ((r) ->
              deferred.notify(r)
              return))

        return deferred.promise)

      openDoc: ((docId, params) ->
        sid = $docflowUtils.splitFullId(docId)
        if not sid.id
          throw Error "Invalid id: #{fullId}"
        $state.transitionTo "doc.#{sid.docType}.form", 'id': sid.id
        if params?.replace
          $location.replace()
        return)

      createDoc: ((docType, params) ->
        sid = $docflowUtils.splitFullId(docType)
        if sid.id
          throw Error "Invalid type: #{fullId}"
        $state.transitionTo "doc.#{sid.docType}.create"
        if params?.replace
          $location.replace()
        return)

      hrefOpenDoc: ((docId, params) ->
        sid = $docflowUtils.splitFullId(docId)
        if not sid.id
          throw Error "Invalid id: #{fullId}"
        return $state.href "doc.#{sid.docType}.form", 'id': sid.id)

      hrefCreateDoc: ((docType, params) ->
        sid = $docflowUtils.splitFullId(docType)
        if sid.id
          throw Error "Invalid type: #{fullId}"
        return $state.transitionTo "doc.#{sid.docType}.create")

      openDocDialog: ((docId, params) ->
        sid = $docflowUtils.splitFullId(docId)
        if not sid.id
          throw Error "Invalid id: #{fullId}"
        deferred = $q.defer()
        $q.when(new editorConstructor(docId, params).loadOrCreate())
        .then((editor) ->
          docDialog = $modal.open(
            templateUrl: "/tmpl/doc/#{sid.docType}?b=#{$docflowConfig.templateBase}&t=formDialog&#{$docflowConfig.tmplParams}"
            backdrop: 'static'
            controller: ['$scope', (($scope) ->
              $scope.itemHeaderUrl = "/tmpl/doc/#{sid.docType}?b=#{$docflowConfig.templateBase}t=formTitle&#{$docflowConfig.tmplParams}"
              $scope.editor = editor
              editor.setEditorDialog docDialog
              editor.controller($scope)
              $scope.$on '$stateChangeStart', (->
                docDialog.close()
                return)
              return)]
          )
          docDialog.result.then ((doc) ->
            deferred.resolve doc
            return)
          return)
        return deferred.promise)

      createDocDialog: ((docType, params) ->
        sid = $docflowUtils.splitFullId(docType)
        if sid.id
          throw Error "Invalid type: #{fullId}"
        deferred = $q.defer()
        $q.when(new editorConstructor(docType, params).loadOrCreate())
        .then((editor) ->
          docDialog = $modal.open(
            templateUrl: "/tmpl/doc/#{sid.docType}?b=#{$docflowConfig.templateBase}&t=formDialog&#{$docflowConfig.tmplParams}"
            backdrop: 'static'
            controller: ['$scope', (($scope) ->
              $scope.itemHeaderUrl = "/tmpl/doc/#{sid.docType}?b=#{$docflowConfig.templateBase}&t=formTitle&#{$docflowConfig.tmplParams}"
              $scope.editor = editor
              editor.setEditorDialog docDialog
              editor.controller($scope)
              $scope.$on '$stateChangeStart', (->
                docDialog.close()
                return)
              return)]
          )
          docDialog.result.then ((doc) ->
            deferred.resolve doc
            return)
          return)
        return deferred.promise)

      navigateBack: (->
        $window.history.back()
        return)

      switchDemoUser: ((demoUser) ->
        s = $location.search()
        if angular.isDefined demoUser
          s.user = demoUser
        else
          delete s.user
        $location.search s
        window.location.href = $location.$$url # force application reload
        return)

  })]
