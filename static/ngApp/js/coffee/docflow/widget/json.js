(function() {
  var module;

  module = angular.module('docflow.widget.json', []);

  module.directive('dfWidgetJson', [
    '$modal', (function($modal) {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          model.$formatters(push((function(value) {
            return JSON.stringify(value, void 0, '  ');
          })));
          model.$parsers.push((function(value) {
            return JSON.parse(value);
          }));
        })
      };
    })
  ]);

  module.directive('dfWidgetJsonView', [
    '$modal', (function($modal) {
      return {
        restrict: 'A',
        scope: true,
        require: 'ngModel',
        link: (function($scope, element, attrs, model) {
          model.$render = (function() {
            element.text(JSON.stringify(model.$viewValue, void 0, '  '));
          });
        })
      };
    })
  ]);

}).call(this);
