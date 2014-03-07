module = angular.module 'docflow.ui.editor', ['docflow.ui.client', 'docflow.config', 'docflow.ui.utils']

module.run ['$docflow', '$docflowEditor', (($docflow, $docflowEditor) ->
  $docflow._setEditor $docflowEditor
  return)]

module.provider '$docflowEditor',
  ['$docflowUtilsProvider', '$docflowConfigProvider', '$injector',
  (($docflowUtilsProvider, $docflowConfigProvider, $injector) ->

    tabTemplatePlugins = {}
    tabScopeExtentions = []

    @registerTabTemplate = ((templateName, tabClass) ->
      tabTemplatePlugins[templateName] = tabClass
      return)

    @registerTabScopeExtention = ((ext) ->
      tabScopeExtentions.push(ext)
      return)

    @$get =
    ['$docflow', '$docflowActions', '$docflowClient', '$docflowUtils', '$docflowConfig', '$q',
    (($docflow, $docflowActions, $docflowClient, $docflowUtils, $docflowConfig, $q) ->

      # defined at factory level, to be accessible while config-phase, to declare Tab implementations
      @Tab = class Tab
        constructor: ((editor, tabName, tabConfig) ->
          @editor = editor
          @name = tabName
          @scope = null
          @removeWatcher = null
          @visible = true
          @config = tabConfig
          if tabConfig
            @docType = tabConfig.docType
            @template = tabConfig.template
            # TODO: Move this away to some module, which will know all about URLes
            @angularTmpl = "/tmpl/doc/#{@docType}?t=#{@template}&item=#{editor.docType}&tab=#{tabName}&#{$docflowConfigProvider.tmplParams}"
          return)
        name: null
        controller: (($scope) ->
          (->
            @scope = $scope
            $scope.$on '$destroy', (=>
              @scope = @removeWatcher = null
              return)
            if @item
              $scope.item = @item
              @setWatcher()
            for ext in tabScopeExtentions
              ext()($scope, @)
            return)
          .call($scope.$parent.tab)
          return)
        setWatcher: (->
          if !@modified and @scope and @item
            @removeWatcher?()
            @removeWatcher = @scope.$watch 'item', ((n, o) =>
              if n != o
                @modified = true
                @removeWatcher()
                @removeWatcher = null
              return), true
          return)
        splitDoc: angular.noop
        mergeDoc: angular.noop
        _modified: false
        Object.defineProperty Tab::, 'modified',
          get: -> @_modified
          set: ((v) ->
            @_modified = v
            @editor.modified = true if v
            return)
        _isNew: undefined
        Object.defineProperty Tab::, 'isNew',
          get: -> if angular.isDefined @_isNew then @_isNew else @editor.isNew
          set: ((v) ->
            @_isNew = v
            return)
        action: ((action, params) ->
          if not @item
            throw Error 'Cannot call action(...) for tab that doesn\'t have .item'
          # TODO: Finish this up!!! ...need to build other types of updates for linked docs
          # $docflowConfig.docs[@docType]?.actions[action].update
          return $docflow.action(@item.id, action,
            params: params
            r: @item.rev
            t: @template
            # TODO: Consider adding doc, if action update = true
          ))
        callParent: (method, args...) ->
          @.__proto__.__proto__[method].apply(@, args)

      # Note: This to overcome the bug in coffee 1.0 - wrong javascript on child class constructor
      Tab.derive = ((childClass, self, editor, tabName, tabConfig) ->
        childClass::__proto__ = Tab::
        Tab::constructor.call(self, editor, tabName, tabConfig)
        return)

      # main-tab implementation
      class MainTab # extends Tab
        constructor: ((editor) ->
          # it's instead of 'extends Tab', since it's broken in coffee 1.0
          Tab.derive(MainTab, @, editor, "_main")
          @item = null
          @docType = editor.docType
          @template = editor.template
          @angularTmpl = "/tmpl/doc/#{@docType}?t=mainTab" # this template is built-in in form.html
          @editorDlg = null
          return)
        splitDoc: ((doc) ->
          @visible = true
          @item = doc
          @scope.item = doc if @scope
          $docflowUtilsProvider.processDocumentBeforeEditing(@item)
          return)
        mergeDoc: ((doc) ->
          angular.extend doc, $docflowUtils.buildDocumentUpdate(@item)
          return)
        action: ((action, params) ->
          # TODO: Replace double-proto by 'super' on coffee update
          @callParent('action', action, params)
          .then((resp) =>
            @editor.setDoc(resp.doc)
            return)
          return)

      # class is defined within $get to have access to injected services
      class Editor
        constructor: ((docId, options) ->
          @docId = docId
          @isNew = $docflowUtilsProvider.isNewDoc(docId)
          @docType = docType = $docflowUtilsProvider.docType(docId)
          @template = template = 'form'
          if options
            @template = template = options.t if options.t
          @docConfig = docConfig = $docflowConfig.docs[docType]
          @modified = false
          if not docConfig
            throw Error "Doctype #{docType} not in docflowConfig"
          @templateConfig = templateConfig = docConfig.templates?[template]
          if not templateConfig
            throw Error "Doctype #{docType} has no template #{@template} in docflowConfig"
          mainTab = new MainTab(@)
          @tabs = tabs = {}
          @selectedTab = tabs[mainTab.name] = mainTab
          if templateConfig.tabs # if extra tabs defined in docflowConfig
            for tabName, tabConfig of templateConfig.tabs
              templateTabPlugin = tabTemplatePlugins[tabConfig.template]?()
              tabs[tabName] =
                if templateTabPlugin
                  new templateTabPlugin(@, tabName, tabConfig)
                else
                  new Tab(@, tabName, tabConfig)
          return)
        Editor.Tab = Tab
        scope: null
        controller: (($scope) ->
          @scope = $scope
          $scope.doc = @doc
          $scope.tab = @selectedTab
          $scope.$on '$destroy', (=>
            @scope = null
            return)
          return)
        setDoc: ((doc) ->
          if doc
            @doc = doc
            docToSplit = angular.copy(doc)
            mainTab = @tabs._main
            for tabName, tab of @tabs
              if tab != mainTab
                tab.splitDoc(docToSplit)
            mainTab.splitDoc(docToSplit)
            if @scope
              @scope.doc = doc
            if not @selectedTab.visible
              @selectTab '_main'
          @modified = false
          for tabName, tab of @tabs
            tab.modified = false
            tab.setWatcher()
          return)
        # loads or creates document model.  returns: promise to editor itself
        loadOrCreate: (->
          if @isNew and not @docConfig.newInstance
            if not @docConfig.$n
              throw Error "Missing $n for docType '#{@docType}' in docflowConfig"
            @setDoc @docConfig.$n
            @modified = true
            return @
          deferred = $q.defer()
          (if @isNew
            $docflowClient.action(@docType, 'newInstance', t: 'form')
            .then ((resp) -> return resp.doc)
          else
            $docflowClient.get(@docId, t: 'form'))
          .then((doc) =>
            @setDoc doc
            deferred.resolve(@)
            return)
          return deferred.promise)
        selectTab: ((tabName, safe) ->
          tab = @tabs[tabName]
          if not tab
            return if safe
            throw Error "Tab with name #{tabName} not found"
          if not tab.visible
            return if safe
            throw Error "Tab #{tabName} is not visible"
          @selectedTab = tab
          @scope.tab = tab if @scope
          return)
        extendScope: (($scope) ->
          $scope.editor = editor
        )
        setEditorDialog: ((dlg) ->
          @editorDlg = dlg
          return)
        save: (->
          return if not @modified
          doc = {}
          for tabName, tab of @tabs
            if tab.modified
              tab.mergeDoc doc
          return $docflowClient.action(@doc.id, (if @isNew then 'create' else 'update'), doc: doc, r: @doc.rev, t: 'form')
          .then((resp) =>
            if @editorDlg
              @editorDlg.close(resp.doc)
            else if @isNew
              $docflowActions.setPreserveActionResultOverUIStateTransition()
              $docflow.openDoc resp.doc.id, replace: true
            else
              @setDoc resp.doc
            return))
        cancel: (->
          if @editorDlg
            @editorDlg.dismiss()
          else if @isNew
            $docflow.navigateBack()
          else
            @setDoc @doc
          return)
        )]

    return)]
