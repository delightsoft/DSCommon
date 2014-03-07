module = angular.module 'app', ['app-common']

module.config ($docflowProvider) ->

  $docflowProvider.init()

  # ATTN: This just a basic implementation.  Actual one should reside in final application!

  return
# End of config
