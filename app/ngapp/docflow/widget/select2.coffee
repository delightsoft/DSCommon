module = angular.module 'docflow.fix.select2', ['docflow.config']

module.directive 'docflowFixSelect2',
[(->
  require: 'ngModel'
  link: ($scope, element, attrs, model) ->

    model.$parsers.unshift (value) ->
      return null unless angular.isArray(value) && value.length > 0
      r = {}
      for v in value
        r[v] = true
      return r # End of parser

    model.$formatters.unshift (value) ->
      return [] unless angular.isObject value
      r = []
      for k, v of value
        if v
          r.push k
      return r # End of parser

    return # End of 'link:'

)] # End of directive 'docflowFixSelect2'