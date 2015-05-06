(function() {
  var module;

  module = angular.module('docflow.widget.container', []);

  module.directive('docflowContainer', [
    '$animate', (function($animate) {
      var controller;
      controller = null;
      return {
        restrict: 'A',
        controller: [
          '$scope', (function($scope) {
            this.count = 0;
            controller = this;
          })
        ],
        link: function($scope, element, attrs) {
          controller.item = (function(visible) {
            var prevVisible;
            prevVisible = !!this.count;
            if (visible) {
              this.count++;
            } else {
              this.count--;
            }
            if (prevVisible !== !!this.count) {
              $animate[!prevVisible ? 'removeClass' : 'addClass'](element, 'ng-hide');
            }
          });
          return $animate.addClass(element, 'ng-hide');
        }
      };
    })
  ]);

  module.directive('docflowContainerItemShow', [
    '$animate', (function($animate) {
      return {
        restrict: 'A',
        require: '^docflowContainer',
        link: (function($scope, element, attrs, container) {
          $scope.$watch(attrs.docflowContainerItemShow, (function(value, prevValue) {
            if (value === prevValue) {
              if (value) {
                container.item(value);
              }
            } else {
              container.item(value);
            }
            $animate[value ? 'removeClass' : 'addClass'](element, 'ng-hide');
          }));
        })
      };
    })
  ]);

}).call(this);
