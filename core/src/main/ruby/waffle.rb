# This is still a work in progress

module Waffle

  # load/require files
  class ScriptLoader
    def ScriptLoader.load_all(prefix, servlet_context)
      @@__servlet_context = servlet_context

      if (prefix.gsub!(/^dir:/, ''))
        @@_ruby_script_path = prefix
        ScriptLoader.load_from_file_system
      else
        servlet_context.getResourcePaths(prefix).each do |path| # this would be for production!!
          require path.gsub(Regexp.new("^#{prefix}"), 'ruby/')
        end
      end
    end

    def ScriptLoader.load_from_file_system
      path = @@__servlet_context.getRealPath('/')
      path = "#{path}#{@@_ruby_script_path}"

      Dir.new(path).each do |entry|
        file = "#{path}#{entry}"
        if File.file?(file) # TODO need to recursively search directories
          load(file) if file =~ /.rb$/
        end
      end
    end
  end

  ##
  # This is a generic class for making HttpServletRequest's, HttpSession's and ServletContext's to act like Hash's
  class WebContext < Hash

    def initialize(context)
      @__context = context
      attribute_names = @__context.get_attribute_names

      while (attribute_names.hasMoreElements)
        name = attribute_names.nextElement

        self[name.to_s] = @__context.get_attribute(name) if name.is_a? Symbol
        self[name] = @__context.get_attribute(name) unless name.is_a? Symbol
      end
    end

    def []=(key, value)
      @__context.set_attribute(key.to_s, value) if key.is_a? Symbol
      @__context.set_attribute(key, value) unless key.is_a? Symbol
      super
    end

    def [](key)
      return super(key.to_s) if key.is_a? Symbol

      return super(key)
    end

    def method_missing(symbol, *args)
      @__context.send(symbol, *args)
    end
  end

  module Controller
    attr_accessor :response # todo the setters for this should be hidden?
    attr_reader :request, :session

    def __set_request(request)
      @request = WebContext.new(request)
    end

    def __set_session(session)
      @session = WebContext.new(session)
    end

    def __pico_container=(pico)
      @@__pico_container = pico
    end

    def method_missing(symbol, *args)
      if symbol.to_s =~ /^find_/ # todo: I don't like "find_" for this ... sounds to model-ish
        key = symbol.to_s
        key = key[5..key.length]
        component = @@__pico_container.getComponentInstance(key)

        return component unless component.nil?
      end

      super(symbol, *args)
    end

  end
end