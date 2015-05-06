(function() {
  var module;

  module = angular.module('docflow.widget.select2', ['docflow.config']);

  module.directive('docflowFixSelect2', [
    (function() {
      return {
        require: 'ngModel',
        link: function($scope, element, attrs, model) {
          model.$parsers.unshift(function(value) {
            var r, v, _i, _len;
            if (!(angular.isArray(value) && value.length > 0)) {
              return null;
            }
            r = {};
            for (_i = 0, _len = value.length; _i < _len; _i++) {
              v = value[_i];
              r[v] = true;
            }
            return r;
          });
          model.$formatters.unshift(function(value) {
            var k, r, v;
            if (!angular.isObject(value)) {
              return [];
            }
            r = [];
            for (k in value) {
              v = value[k];
              if (v) {
                r.push(k);
              }
            }
            return r;
          });
        }
      };
    })
  ]);

}).call(this);
