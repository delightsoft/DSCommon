module = angular.module 'docflow.widget.popover', ['docflow.config']

module.directive 'docflowWidgetPopover',
[(->
  link: ($scope, element, attrs) ->

    if attrs.docflowWidgetPopover
      params = $scope.$eval attrs.docflowWidgetPopover

    content = element.children()[0]

    options =
      title: '...' # must be something, to make tooltip working
      placement: 'right'
      trigger: 'click'

    if angular.isObject params
      angular.extend options, params

    if options.content == "next"
      options.template = element.next()[0]
    else
      options.template = element.children()[0]

    $(options.template).find('[docflow-widget-popover-close]').click ->
      element.tooltip 'hide'
      return # End of click()

    clickTrigger = options.trigger == 'click'
    if clickTrigger
      options.trigger = 'manual'
      element.click ->
        element.tooltip 'toggle'
        return false

    element.tooltip options

    return # End of 'link:'

)] # End of directive 'infoWidget'