function [post nlZ dnlZ] = infKL_sKL(hyp, mean, cov, lik, x, y)
% stochastic KL-Proximal Variational Gaussian Inference 
if hyp.is_cached==1
	global cache_post;
	global cache_nlz;
	global cache_idx;
	
	post=cache_post(cache_idx);
	nlZ=cache_nlz(cache_idx);
	if nargout>2
		warning('to be implemented\n');
		dnlZ = NaN;
	end
	return 
end

lik_name = func2str(lik{1});

snu2=hyp.snu2;
x_batch=x;
y_batch=y;
n_batch=size(x_batch,1);

% GP prior
K_batch = feval(cov{:}, hyp.cov, x_batch);                  % evaluate the covariance matrix
m_batch = feval(mean{:}, hyp.mean, x_batch);                      % evaluate the mean vector
K_batch=snu2*eye(n_batch)+K_batch;

%the size of mini batch= n_batch * mini_batch_rate
mini_batch_size=hyp.mini_batch_size;
assert (mini_batch_size>0)
mini_batch_num=ceil(n_batch/mini_batch_size);
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
m_u_batch=hyp.init_m;
C_u_batch = chol(hyp.init_V,'lower');
C_u_batch = C_u_batch-diag(diag(C_u_batch))+diag(log(diag(C_u_batch)));
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

assert(isfield(hyp,'learning_rate'))

%algorithms={'adadelta','momentum','rmsprop','adagrad','smorms3'};
if isfield(hyp,'opt_alg')
	switch hyp.opt_alg
	case 'momentum'
		momentum_m_u_batch=zeros(n_batch,1);
		momentum_C_u_batch=zeros(n_batch,n_batch);
	case 'adadelta'
		assert(isfield(hyp,'epsilon'))
		assert(isfield(hyp,'decay_factor'))
		g_acc_m_u_batch=zeros(n_batch,1);
		g_delta_acc_m_u_batch=zeros(n_batch,1);
		g_acc_C_u_batch=zeros(n_batch,n_batch);
		g_delta_acc_C_u_batch=zeros(n_batch,n_batch);
	case 'rmsprop'
		assert(isfield(hyp,'epsilon'))
		assert(isfield(hyp,'decay_factor'))
		g_acc_m_u_batch=zeros(n_batch,1);
		g_acc_C_u_batch=zeros(n_batch,n_batch);
	case 'adagrad'
		assert(isfield(hyp,'epsilon'))
		g_acc_m_u_batch=zeros(n_batch,1);
		g_acc_C_u_batch=zeros(n_batch,n_batch);
	case 'smorms3'
		assert(isfield(hyp,'epsilon'))

		g_acc_m_u_batch=zeros(n_batch,1);
		g_acc_square_m_u_batch=zeros(n_batch,1);
		mem_m_u_batch=ones(n_batch,1);

		g_acc_C_u_batch=zeros(n_batch,n_batch);
		g_acc_square_C_u_batch=zeros(n_batch,n_batch);
		mem_C_u_batch=ones(n_batch,n_batch);
	otherwise
		error('do not support')
	end

end
assert(~isfield(hyp,'momentum'))
assert(~isfield(hyp,'adadelta'))
iter = 0;
pass=0;
max_pass=hyp.max_pass;
while pass<max_pass
	index=randperm(n_batch);
	offset=0;
	mini_batch_counter=0;
	while mini_batch_counter<mini_batch_num
		iter = iter + 1;

		to_idx=(mini_batch_counter+1)*mini_batch_size;
		if to_idx>n_batch
			to_idx=n_batch;
		end
		from_idx=mini_batch_counter*mini_batch_size+1;

		idx=index(from_idx:to_idx);
		x=x_batch(idx,:);
		y=y_batch(idx);

		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
		%mini batch
		rate=hyp.learning_rate;
		weight=double(n_batch)/size(x,1);

		alpha_batch=K_batch\(m_u_batch-m_batch);
		post_m_batch=m_u_batch;
		C_batch = C_u_batch-diag(diag(C_u_batch))+diag(exp(diag(C_u_batch)));
		post_v_batch=sum(C_batch'.*C_batch',1)';

		if hyp.stochastic_approx==1
			[ll, gf, gv] = sampling_E(y, post_m_batch(idx), post_v_batch(idx), lik, hyp.sample_size, hyp.lik);
		else
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll, gf, gv] = E_log_p(lik_name, y, post_m_batch(idx), post_v_batch(idx), hyp.lik);
			otherwise	 
				[ll,gf,g2f,gv] = likKL(post_v_batch(idx), lik, hyp.lik, y, post_m_batch(idx));
			end
		end

		%using unbaised weight
		gf = gf .* weight;
		gv = gv .* weight;

		%mapping the change in a mini_batch to the change in the whole batch 
		df_batch=zeros(n_batch,1);
		df_batch(idx)=gf;

		dv_batch=zeros(n_batch,1);
		dv_batch(idx)=gv;

		g_rate=rate/(iter).^(hyp.power);
		g_m_u_batch=alpha_batch-df_batch;

		g_C_u_batch=tril(K_batch\C_batch - diag(2.0.*dv_batch)*C_batch);
		g_C_u_batch=g_C_u_batch-diag(diag(g_C_u_batch)) +diag(diag(g_C_u_batch) .* diag(C_batch) )+ diag(-1 .*ones(n_batch,1));

		if isfield(hyp,'opt_alg')
			switch hyp.opt_alg
			case 'momentum'
				momentum_m_u_batch=hyp.momentum .* momentum_m_u_batch-g_rate .*g_m_u_batch;
				m_u_batch=m_u_batch+momentum_m_u_batch;
				momentum_C_u_batch=hyp.momentum .* momentum_C_u_batch-g_rate .*g_C_u_batch;
				C_u_batch=C_u_batch+momentum_C_u_batch;
			case 'adadelta'
				decay_factor=hyp.decay_factor;
				epsilon=hyp.epsilon;
				learning_rate=hyp.learning_rate;

				[g_m_u_batch,g_acc_m_u_batch,g_delta_acc_m_u_batch] = adadelta_update(g_m_u_batch,g_acc_m_u_batch,g_delta_acc_m_u_batch,decay_factor,epsilon,learning_rate);
				m_u_batch=m_u_batch-g_m_u_batch;

				[g_C_u_batch,g_acc_C_u_batch,g_delta_acc_C_u_batch] = adadelta_update(g_C_u_batch,g_acc_C_u_batch,g_delta_acc_C_u_batch,decay_factor,epsilon,learning_rate);
				C_u_batch=C_u_batch-g_C_u_batch;

			case 'rmsprop'
				decay_factor=hyp.decay_factor;
				epsilon=hyp.epsilon;
				learning_rate=hyp.learning_rate;

				[g_m_u_batch,g_acc_m_u_batch] = rmsprop_update(g_m_u_batch,g_acc_m_u_batch,decay_factor,epsilon,learning_rate);
				m_u_batch=m_u_batch-g_m_u_batch;

				[g_C_u_batch,g_acc_C_u_batch] = rmsprop_update(g_C_u_batch,g_acc_C_u_batch,decay_factor,epsilon,learning_rate);
				C_u_batch=C_u_batch-g_C_u_batch;

			case 'adagrad'
				epsilon=hyp.epsilon;
				learning_rate=hyp.learning_rate;

				[g_m_u_batch,g_acc_m_u_batch] = adagrad_update(g_m_u_batch,g_acc_m_u_batch,epsilon,learning_rate);
				m_u_batch=m_u_batch-g_m_u_batch;

				[g_C_u_batch,g_acc_C_u_batch] = adagrad_update(g_C_u_batch,g_acc_C_u_batch,epsilon,learning_rate);
				C_u_batch=C_u_batch-g_C_u_batch;

			case 'smorms3'
				epsilon=hyp.epsilon;
				learning_rate=hyp.learning_rate;

				[g_m_u_batch,g_acc_m_u_batch,g_acc_square_m_u_batch,mem_m_u_batch] = smorms3_update(g_m_u_batch,g_acc_m_u_batch,g_acc_square_m_u_batch,mem_m_u_batch, epsilon,learning_rate);
				m_u_batch=m_u_batch-g_m_u_batch;

				[g_C_u_batch,g_acc_C_u_batch,g_acc_square_C_u_batch,mem_C_u_batch] = smorms3_update(g_C_u_batch,g_acc_C_u_batch,g_acc_square_C_u_batch,mem_C_u_batch,epsilon,learning_rate);
				C_u_batch=C_u_batch-g_C_u_batch;

			otherwise
				error('do not support')
			end
		else
			m_u_batch=m_u_batch-g_rate .*g_m_u_batch;
			C_u_batch=C_u_batch-g_rate.*g_C_u_batch;
		end

		%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

		mini_batch_counter=mini_batch_counter+1;

		if isfield(hyp,'save_iter') && hyp.save_iter==1
			global cache_nlz_iter
			global cache_iter

			post_m_batch=m_u_batch;
			alpha_batch=K_batch\(m_u_batch-m_batch);
			C_batch = C_u_batch-diag(diag(C_u_batch))+diag(exp(diag(C_u_batch)));
			post_v_batch=sum(C_batch'.*C_batch',1)';
			switch lik_name
			case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
				[ll_iter, df_batch, dv_batch] = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
			otherwise	 
				[ll_iter,df_batch,d2f_batch,dv_batch] = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
			end
			W_batch=-2.0*dv_batch;
			sW_batch=sqrt(abs(W_batch)).*sign(W_batch);
			nlZ_batch2=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);

			cache_iter=[cache_iter; iter];
			cache_nlz_iter=[cache_nlz_iter; nlZ_batch2];
		end

	end
	pass=pass+1;

	%display nlz
	post_m_batch=m_u_batch;
	alpha_batch=K_batch\(m_u_batch-m_batch);
	C_batch = C_u_batch-diag(diag(C_u_batch))+diag(exp(diag(C_u_batch)));
	post_v_batch=sum(C_batch'.*C_batch',1)';
	switch lik_name
	case {'laplace','likLaplace','poisson','bernoulli_logit','likLogistic'}
		[ll_iter, df_batch, dv_batch] = E_log_p(lik_name, y_batch, post_m_batch, post_v_batch, hyp.lik);
	otherwise	 
		[ll_iter,df_batch,d2f_batch,dv_batch] = likKL(post_v_batch, lik, hyp.lik, y_batch, post_m_batch);
	end
	W_batch=-2.0*dv_batch;
	sW_batch=sqrt(abs(W_batch)).*sign(W_batch);
	nlZ_batch=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
	fprintf('pass:%d) %.4f\n', pass, nlZ_batch);

	if hyp.is_save==1
		global cache_post;
		global cache_nlz;

		L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch);
		post.sW = sW_batch;                                           
		post.alpha = alpha_batch;
		post.L = L_batch;                                    

		cache_post=[cache_post; post];
		cache_nlz=[cache_nlz; nlZ_batch];
	end
end
L_batch = chol(eye(n_batch)+sW_batch*sW_batch'.*K_batch); %L = chol(sW*K*sW + eye(n)); 
post.sW = sW_batch;                                             % return argument
post.alpha = alpha_batch;
post.L = L_batch;                                              % L'*L=B=eye(n)+sW*K*sW

nlZ=batch_nlz_fullv2(lik, hyp, sW_batch, K_batch, m_batch, alpha_batch, post_m_batch, y_batch);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end
end


function [grad,g_acc,g_delta_acc] = adadelta_update(gradient,g_acc,g_delta_acc,decay_factor,epsilon,learning_rate)
	g_acc=decay_factor .* g_acc + (1.0-decay_factor) .* (gradient.^2);
	grad= (learning_rate .* gradient .* sqrt(g_delta_acc + epsilon) ./ sqrt(g_acc+epsilon) );
	g_delta_acc=decay_factor .* g_delta_acc + (1.0-decay_factor) .* (grad.^2);
end

function [grad,g_acc] = rmsprop_update(gradient,g_acc,decay_factor,epsilon,learning_rate)
	g_acc=decay_factor .* g_acc + (1.0-decay_factor) .* (gradient.^2);
	grad=learning_rate .* gradient ./ sqrt(g_acc+epsilon);
end

function [grad,g_acc] = adagrad_update(gradient,g_acc,epsilon,learning_rate)
	g_acc=g_acc + (gradient.^2);
	grad=learning_rate .* gradient ./ sqrt(g_acc+epsilon);
end

function [grad,g_acc,g_acc_square,mem] = smorms3_update(gradient,g_acc,g_acc_square,mem,epsilon,learning_rate)
	r=1.0./(mem+1.0);
	g_acc=(1.0-r) .* g_acc + r .* gradient;
	g_acc_square=(1.0-r) .* g_acc_square + r .* (gradient.^2);

	tmp=(g_acc.^2) ./ (g_acc_square+epsilon);
	grad=gradient.* min(learning_rate, tmp) ./ (sqrt(g_acc_square) + epsilon);
	mem=1.0 + mem .* (1.0 - tmp); 
end
