(function() {
  var module;

  module = angular.module('docflow.misc.resizeItemContent', []);

  module.directive('docflowResizeItemContent', [
    '$log', (function($log) {
      return {
        restrict: 'A',
        scope: false,
        replace: false,
        transclude: false,
        link: (function($scope, element, attrs) {
          var bottom, content, fixContentHeight, options, params;
          if (attrs.docflowResizeItemContent) {
            params = $scope.$eval(attrs.docflowResizeItemContent);
          }
          options = {};
          if (angular.isObject(params)) {
            angular.extend(options, params);
          }
          bottom = element.find(options.bottomClass)[0];
          if (!bottom) {
            $log.warn("docflow/misc/resizeItemContent: Element with class " + options.bottomClass + " not found");
          } else {
            content = element.find(options.contentClass);
            fixContentHeight = function() {
              var bottomTop;
              bottomTop = bottom.getBoundingClientRect().top;
              content.each(function() {
                var height;
                height = bottomTop - this.getBoundingClientRect().top - 20;
                return $(this).css({
                  'height': height
                });
              });
            };
            fixContentHeight();
            $(window).resize(function() {
              fixContentHeight();
            });
          }
        })
      };
    })
  ]);

}).call(this);
