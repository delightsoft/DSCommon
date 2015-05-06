(function() {
  var module;

  module = angular.module('docflow.widget.textarea.size', ['docflow.config']);

  module.directive('docflowWidgetTextareaSize', [
    (function() {
      return {
        restrict: 'A',
        require: 'ngModel',
        link: function($scope, element, attrs, model) {
          if (!model.$viewValue) {
            return;
          }
          model.$formatters.push(function(model) {
            var length, rows;
            length = model.length;
            if (length / 23 > 8) {
              rows = 8;
            } else {
              rows = Math.ceil(length / 23 + 1);
            }
            $(element[0]).attr('rows', rows);
            return model;
          });
        }
      };
    })
  ]);

}).call(this);
