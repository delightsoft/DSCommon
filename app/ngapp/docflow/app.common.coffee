module = angular.module 'app.common', [
  'docflow'
  'docflow.ui.tabLinkedDocument'
  'docflow.ui.form' # docflow-form directive
  'docflow.ui.table' # docflow-table directive

  'docflow.templates'

      # modal from ui-bootstrap
      'ui.bootstrap.modal',
    #  'template/modal/window.html'
    #  'template/modal/backdrop.html'

      # standard pager from ui-bootstrap for lists
    #  'template/pagination/pagination.html',

  # modules from ui-bootstrap
  'ui.bootstrap.pagination', # usage: paging in lists

  # modules from ui-utils
  'ui.utils', # usage: ui.keypress

  # File: \static\libs\ui-sortable\sortable.js
  'ui.sortable', # Contains directive 'uiSortable'. TODO: Do we really need this on core?


  # File: \static\libs\ui-select2\select2.js
  'ui.select2',
  'docflow.fix.select2', # changes model format for ui.select2

  # TODO: Optimize list of directives.  Current everything gets initialized.
  # TODO: Reconsider core widgets.  Some could be not in use or simply weak
  'docflow.widget.popover'
  'docflow.widget.container'
  'docflow.widget.datetime'
  'docflow.widget.dropdown'
  'docflow.widget.enum'
  'docflow.widget.password'
  'docflow.widget.phone'
  'docflow.widget.selector'
  'docflow.widget.tags'
  'docflow.widget.state'
  'docflow.widget.textarea.size'
  'docflow.widget.subtable'
  'docflow.widget.scrollspy'
  'docflow.widget.texteditor'
  'docflow.widget.photo.control'

  'docflow.misc.resizeItemContent'


  'ui.router' # ui-router
]

module.config ['$stateProvider', '$urlRouterProvider', '$locationProvider',
  (($stateProvider, $urlRouterProvider, $locationProvider) ->
    $locationProvider.html5Mode true
    return)]

module.run(
  ['$docflow', '$docflowConfig', '$state', '$location', '$rootScope', '$anchorScroll', '$sce',
  (($docflow, $docflowConfig, $state, $location, $rootScope, $anchorScroll, $sce) ->

    $rootScope.$angular = angular
    $rootScope.$state = $state
    $rootScope.$docflow = $docflow
    $rootScope.$sce = $sce
    $rootScope.pageTitle = ""
    # sets value to right pageTitle from any inner $rootScope
    $rootScope.setPageTitle = ((title) ->
      $rootScope.pageTitle = title
      $state.current?.pageTitle = title
      return)
    # TODO: We have some issues with scrollTo in reports interface. Fix-it!!!
    $rootScope.scrollTo = ((id, event) ->
      $location.hash id
      $anchorScroll()
      if event
        event.preventDefault()
        event.stopPropagation()
      return)

    return)]
)

# Directive to be used in formTitle and others
module.directive 'docflowPageTitle', (->
  return {
    restrict: 'A'
    link: ($scope, element, attrs) ->
      attrs.$observe 'pageTitle', ((title) ->
        $scope.setPageTitle(title || '')
        return)
  })

# TODO: It that we right way to set pager localization
# Changes ui-bootstrap pager look by l18n mechanism
module.constant 'paginationConfig', {
  boundaryLinks: false
  directionLinks: true
  firstText: '<<'
  previousText: '<'
  nextText: '>'
  lastText: '>>'}
