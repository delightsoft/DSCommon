module = angular.module 'docflow.widget.state', []

module.directive 'docflowState',
['$docflowUtils', '$docflowConfig', (($docflowUtils, $docflowConfig) ->
  restrict: 'A'
  require: 'ngModel'
  scope: true
  link: (($scope, element, attrs, ngModel) ->
    ngModel.$render = (->
      if $scope.state
        if $scope.state.color
          element.removeClass "df-state-#{$scope.state.color}"
        $scope.state = null
      item = ngModel.$viewValue
      if item
        sid = $docflowUtils.splitFullId item.id
        states = $docflowConfig.docs[sid.docType].states
        $scope.state = state = states[item.state]
        element.text state.title
        if state.color
          element.addClass "df-state-#{state.color}"
      else
        element.text ''
      return)
    return)
)]
