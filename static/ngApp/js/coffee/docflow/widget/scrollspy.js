(function() {
  var module;

  module = angular.module('docflow.widget.scrollspy', ['docflow.config']);

  module.directive('docflowWidgetScrollspy', [
    '$timeout', '$window', (function($timeout, $window) {
      return {
        restrict: 'A',
        link: function($scope, element, attrs) {
          var options;
          options = attrs.docflowWidgetScrollspy ? $scope.$eval(attrs.docflowWidgetScrollspy) : {};
          $timeout(function() {
            return element.scrollspy(options);
          });
        }
      };
    })
  ]);

}).call(this);
