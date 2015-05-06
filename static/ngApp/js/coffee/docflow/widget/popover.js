(function() {
  var module;

  module = angular.module('docflow.widget.popover', ['docflow.config']);

  module.directive('docflowWidgetPopover', [
    (function() {
      return {
        link: function($scope, element, attrs) {
          var clickTrigger, content, options, params;
          if (attrs.docflowWidgetPopover) {
            params = $scope.$eval(attrs.docflowWidgetPopover);
          }
          content = element.children()[0];
          options = {
            title: '...',
            placement: 'right',
            trigger: 'click'
          };
          if (angular.isObject(params)) {
            angular.extend(options, params);
          }
          if (options.content === "next") {
            options.template = element.next()[0];
          } else {
            options.template = element.children()[0];
          }
          $(options.template).find('[docflow-widget-popover-close]').click(function() {
            element.tooltip('hide');
          });
          clickTrigger = options.trigger === 'click';
          if (clickTrigger) {
            options.trigger = 'manual';
            element.click(function() {
              element.tooltip('toggle');
              return false;
            });
          }
          element.tooltip(options);
        }
      };
    })
  ]);

}).call(this);
