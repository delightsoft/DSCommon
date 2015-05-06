module = angular.module 'docflow.widget.id', []

module.directive 'docflowIdScope',
[(->
  restrict: 'A'
  scope: true
  controller: ['$scope', (($scope) ->
    @scope = $scope
    return)]
)]

module.directive 'docflowId',
[(->
  restrict: 'A'
  require: '^?docflowIdScope'
  link: (($scope, element, attrs, docflowIdScope) ->
    if attrs.id
      throw new Error("Element already has 'id' attribute");
    if docflowIdScope
      $(element).attr 'id', if attrs.docflowId
          "#{attrs.docflowId}-#{docflowIdScope.scope.$id}"
        else
          docflowIdScope.scope.$id
    return)
)]

module.directive 'docflowFor',
[(->
  restrict: 'A'
  require: '^?docflowIdScope'
  link: (($scope, element, attrs, docflowIdScope) ->
    if attrs.for
      throw new Error("Element already has 'for' attribute");
    if docflowIdScope
      $(element).attr 'for', if attrs.docflowFor
          "#{attrs.docflowFor}-#{docflowIdScope.scope.$id}"
        else
          docflowIdScope.scope.$id
    return)
)]
