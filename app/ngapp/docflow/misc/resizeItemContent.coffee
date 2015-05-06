module = angular.module 'docflow.misc.resizeItemContent', []

module.directive 'docflowResizeItemContent',
  ['$log',
  (($log) ->
      restrict: 'A'
      scope: false
      replace: false
      transclude: false
      link: (($scope, element, attrs) ->

        if attrs.docflowResizeItemContent
          params = $scope.$eval attrs.docflowResizeItemContent

        options = {}

        if angular.isObject params
          angular.extend options, params

        bottom = element.find(options.bottomClass)[0]

        if not bottom
            $log.warn "docflow/misc/resizeItemContent: Element with class #{options.bottomClass} not found"
        else
          content = element.find options.contentClass
          fixContentHeight = ->
            bottomTop = bottom.getBoundingClientRect().top
            content.each ->
              height = bottomTop - @getBoundingClientRect().top - 20
              $(@).css
                'height': height
            return

          fixContentHeight()

          $(window).resize -> fixContentHeight(); return

        return)
  )]