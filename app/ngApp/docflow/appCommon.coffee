module = angular.module 'docflow.appCommon', [
  'ui.router'
]

module.config ['$stateProvider', '$urlRouterProvider', '$locationProvider',
  (($stateProvider, $urlRouterProvider, $locationProvider) ->
    $locationProvider.html5Mode true
    return)]

module.run(
  ['$state', '$location', '$rootScope', '$anchorScroll',
  (($state, $location, $rootScope, $anchorScroll) ->

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

# TODO: Fix that we right way to set pager localization
# Changes ui-bootstrap pager look by l18n mechanism
module.constant 'paginationConfig', {
  boundaryLinks: false
  directionLinks: true
  firstText: '<<'
  previousText: '<'
  nextText: '>'
  lastText: '>>'}
