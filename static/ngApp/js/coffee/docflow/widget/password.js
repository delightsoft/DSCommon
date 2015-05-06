(function() {
  var module;

  module = angular.module('docflow.widget.password', ['docflow.config']);

  module.directive('docflowWidgetPassword', [
    (function() {
      return {
        restrict: 'A',
        scope: true,
        require: 'ngModel',
        link: function($scope, element, attrs, model) {
          $scope.mode = 'changeButton';
          $scope.changePassword = (function() {
            var updateModel;
            $scope.mode = 'passwordFields';
            model.$render = (function() {
              $scope.pwd1 = '';
              $scope.pwd2 = '';
              $scope.mode = 'changeButton';
            });
            $scope.pwd1 = '';
            $scope.pwd2 = '';
            updateModel = (function() {
              var p1, p2;
              p1 = $scope.pwd1.trim();
              p2 = $scope.pwd2.trim();
              model.$setViewValue(p1 === p2 && p1 !== '' ? p1 : null);
            });
            $scope.$watch('pwd1', updateModel);
            $scope.$watch('pwd2', updateModel);
          });
        }
      };
    })
  ]);

  module.directive('docflowWidgetPasswordMatch', [
    (function() {
      return {
        restrict: 'A',
        scope: false,
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          model.$parsers.push((function(value) {
            model.$setValidity('passwordMatch', value.trim() === $scope.pwd1.trim());
            return value;
          }));
        })
      };
    })
  ]);

}).call(this);
