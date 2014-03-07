module = angular.module 'docflow.widget.textarea.size', ['docflow.config']

module.directive 'docflowWidgetTextareaSize', [
  ( ->
    restrict: 'A'
    require: 'ngModel'
    link: ($scope, element, attrs, model) ->

      if !model.$viewValue then return

      model.$formatters.push (model)->

        length = model.length
        if length/23 > 8 then rows = 8 else rows = Math.ceil length/23+1

        $(element[0]).attr 'rows', rows


        return model




      return

  )]

