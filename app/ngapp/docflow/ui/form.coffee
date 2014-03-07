module = angular.module 'docflow.ui.form', []

module.directive 'docflowForm', [(->
  restrict: 'A'
  scope: false
  link: ($scope, element, attrs) ->
    # true, if field viewable
    $scope.v = ((fld, item) -> return item.hasOwnProperty(fld) && !u(fld, item))
    # true, if field editable
    $scope.u = u = ((fld, item) -> item._u?.hasOwnProperty fld)
    # true, if field either editable or viewable
    $scope.vu = ((fld, item) -> return item.hasOwnProperty(fld) || item._u?.hasOwnProperty(fld))
    # checks, that all editable fields are valid
    $scope.fv = ((form, item) ->
      if form
        for k of item._u
          if form[k]?.$invalid
            return false
      return true)
    return)]
