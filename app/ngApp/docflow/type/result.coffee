module = angular.module 'docflow.type.result', [
  'ui.bootstrap.modal'
  'template/modal/window.html'
  'template/modal/backdrop.html'
]

module.directive 'dfTypeResult',
  ['$modal',
  (($modal) ->
    restrict: 'C'
    scope: true
    require: 'ngModel'
    link: (($scope, element, attrs, model) ->
      $scope.showMessages = (->
        $modal.open(
          windowClass: 'df-area-result-popup'
          template: '<i class="fa fa-times" ng-click="$close()"></i>' + model.$viewValue.messages.html
          backdrop: 'static'
        )
        return)
      return)
    )]

