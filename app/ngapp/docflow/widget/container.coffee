module = angular.module 'docflow.widget.container', []

module.directive 'docflowContainer',
['$animate', (($animate) ->
  controller = null
  return {
    restrict: 'A'
    controller: ['$scope', (($scope) ->
      @count = 0
      controller = @
      return)]
    link: ($scope, element, attrs) ->
      controller.item = ((visible) ->
        prevVisible = !!@count
        if visible
          @count++
        else
          @count--
        if prevVisible != !!@count
          $animate[if not prevVisible then 'removeClass' else 'addClass'](element, 'ng-hide')
        return)
      $animate.addClass(element, 'ng-hide')
  }
)]

module.directive 'docflowContainerItemShow',
['$animate', (($animate) ->
  restrict: 'A'
  require: '^docflowContainer'
  link: (($scope, element, attrs, container) ->
    $scope.$watch(attrs.docflowContainerItemShow, ((value, prevValue) ->
      if value == prevValue
        if value
          container.item(value)
      else
          container.item(value)
      $animate[if value then 'removeClass' else 'addClass'](element, 'ng-hide')
      return))
    return)
)]
