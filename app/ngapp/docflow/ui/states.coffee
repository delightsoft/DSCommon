module = angular.module 'docflow.ui.states', ['ui.router.state', 'docflow.config']

module.config(
  ['$stateProvider', '$docflowConfigProvider',
  (($stateProvider, $docflowConfigProvider) ->

    $stateProvider.state
      name: "doc"
      url: "/doc"
      templateUrl: (-> return "/tmpl/doc/index?#{$docflowConfigProvider.tmplParams}")

    # for every document in configuration
    for docType, doc of $docflowConfigProvider.docs

      ((docType) =>

        # document root state
        $stateProvider.state
          name: "doc.#{docType}"

        # list state
        $stateProvider.state
          url: "/#{docType}"
          name: "doc.#{docType}.list"
          views:
            '@doc':
              templateUrl: (-> return "/tmpl/doc/#{docType}?t=list&#{$docflowConfigProvider.tmplParams}")
              controller: (($scope) ->
                $scope.values = {} # collection to keep filtering controls values
                return)

        # document form/create states
        formEditCreateController = (($scope, editor,  $docflowClient) ->
            $scope.itemHeaderUrl = "/tmpl/doc/#{docType}?t=formTitle&#{$docflowConfigProvider.tmplParams}"
            $scope.editor = editor
            editor.controller($scope)
            return)

        $stateProvider.state
          name: "doc.#{docType}.create"
          url: "/#{docType}/new"
          resolve:
            editor: ['$docflowEditor', '$stateParams', '$location', '$log',
              (($docflowEditor, $stateParams, $location, $log) ->
                try
                  return addUrlArgSupport(
                    new $docflowEditor("#{docType}"),
                    $location, $log)
                  .loadOrCreate()
                catch e
                  $log.error e
                  throw e
              )]
          views:
            '@doc':
              templateUrl: (-> return "/tmpl/doc/#{docType}?t=form&#{$docflowConfigProvider.tmplParams}")
              controller: formEditCreateController

        $stateProvider.state
          name: "doc.#{docType}.form"
          url: "/#{docType}/{id}"
          resolve:
            editor: ['$docflowEditor', '$stateParams', '$location', '$log',
              (($docflowEditor, $stateParams, $location, $log) ->
                try
                  return addUrlArgSupport(
                    new $docflowEditor("#{docType}@#{$stateParams.id}"),
                    $location, $log)
                  .loadOrCreate()
                catch e
                  $log.error e
                  throw e
              )]
          views:
            '@doc':
              templateUrl: (-> return "/tmpl/doc/#{docType}?t=form&#{$docflowConfigProvider.tmplParams}")
              controller: formEditCreateController

        # item history state
#        $stateProvider.state
#          url: "/history"
#          name: "doc.#{docType}.form.history"
#          views:
#            'mainArea':
#              templateUrl: (-> return "/tmpl/history/itemHistory?#{$docflowConfigProvider.tmplParams}")

        # item comments state
        state = $stateProvider.state
          name: "doc.#{docType}.form.comments"
          url: "/comments"
#          views:
#            "@doc.#{docType}.form":
#              templateUrl: (-> return "/tmpl/comments/itemComments?#{$docflowConfigProvider.tmplParams}")
        state["@doc.#{docType}.form"] =
          templateUrl: (-> return "/tmpl/comments/itemComments?#{$docflowConfigProvider.tmplParams}")

        return)(docType)

    return)
  ])

# extends $docflowEditor, so it would take and update paramter 'tab' in $location.search()
addUrlArgSupport = ((editor, $location, $log) ->
  # intercept controller()
  superController = editor.controller
#  old = $location.search
#  $location.search = ((v)->
#    if v
#      $log.info v
#    return old.apply($location, arguments))
  editor.controller = (($scope) ->
    editor.selectTab($location.search().tab, true)
    superController.call(editor, $scope)
    return)
  # intercept selectTab()
  superSelectTab = editor.selectTab
  editor.selectTab = ((tabName, safe) ->
    s = $location.search()
    oldTab = s.tab
    superSelectTab.call(editor, tabName, safe)
    newTab = editor.selectedTab.name
    if (newTab == '_main' && angular.isDefined(oldTab)) || newTab != oldTab
      if newTab == '_main'
        delete s.tab
      else
        s.tab = newTab
      $location.search(s)
      $location.replace()
    return)
  return editor)
