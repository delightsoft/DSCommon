(function() {
  var module;

  module = angular.module('docflow.widget.state', []);

  module.directive('docflowState', [
    '$docflowUtils', '$docflowConfig', (function($docflowUtils, $docflowConfig) {
      return {
        restrict: 'A',
        require: 'ngModel',
        scope: true,
        link: (function($scope, element, attrs, ngModel) {
          ngModel.$render = (function() {
            var item, sid, state, states;
            if ($scope.state) {
              if ($scope.state.color) {
                element.removeClass("df-state-" + $scope.state.color);
              }
              $scope.state = null;
            }
            item = ngModel.$viewValue;
            if (item) {
              sid = $docflowUtils.splitFullId(item.id);
              states = $docflowConfig.docs[sid.docType].states;
              $scope.state = state = states[item.state];
              element.text(state.title);
              if (state.color) {
                element.addClass("df-state-" + state.color);
              }
            } else {
              element.text('');
            }
          });
        })
      };
    })
  ]);

}).call(this);
