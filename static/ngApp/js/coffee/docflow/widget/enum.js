(function() {
  var module;

  module = angular.module('docflow.widget.enum', []);

  module.directive('docflowEnum', [
    (function() {
      return {
        restrict: 'A',
        require: 'ngModel',
        scope: true,
        link: (function($scope, element, attrs, ngModel) {
          var l18n;
          l18n = $scope.$eval(attrs.docflowEnum);
          ngModel.$render = (function() {
            var val;
            if ($scope.prevVal) {
              if ($scope.prevVal.color) {
                element.removeClass("df-enum-" + $scope.prevVal.color);
              }
              $scope["enum"] = null;
            }
            if (ngModel.$viewValue) {
              $scope.prevVal = val = l18n[ngModel.$viewValue];
              if (angular.isDefined(val)) {
                element.text(val.title);
                if (val.color) {
                  element.addClass("df-enum-" + val.color);
                }
              } else {
                element.text(ngModel.$viewValue);
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
