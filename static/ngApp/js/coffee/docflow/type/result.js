(function() {
  var module;

  module = angular.module('docflow.type.result', ['ui.bootstrap.modal', 'template/modal/window.html', 'template/modal/backdrop.html']);

  module.directive('dfTypeResult', [
    '$modal', (function($modal) {
      return {
        restrict: 'C',
        scope: true,
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          $scope.showMessages = (function() {
            $modal.open({
              windowClass: 'df-area-result-popup',
              template: '<i class="fa fa-times" ng-click="$close()"></i>' + model.$viewValue.messages.html,
              backdrop: 'static'
            });
          });
        })
      };
    })
  ]);

}).call(this);
